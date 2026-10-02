package com.birdware.core;

import com.birdware.BirdWare;
import com.birdware.event.EventPriority;
import com.birdware.event.Subscribe;
import com.birdware.event.events.TickEvent;
import com.birdware.event.events.WorldChangeEvent;
import com.birdware.mixin.core.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Shared inventory access for every automation module.
 *
 * <h2>Slot numbering</h2>
 * All methods use <b>inventory indices</b>: 0-8 hotbar, 9-35 main inventory, 36-39 armor (feet, legs, chest, head),
 * 40 offhand. They are translated to menu slot ids of whatever container is open via {@link #toMenuSlot(int)}.
 *
 * <h2>Coordination</h2>
 * <ul>
 *     <li><b>Hotbar ownership</b>: {@link #requestSlot(Object, int, int)} lets one module at a time hold the selected
 *     slot (highest priority wins, preempting lower ones). When the owner releases it, the slot that was selected
 *     before the first request is restored. AutoEat holding food can therefore not be undone by AutoWeapon.</li>
 *     <li><b>Click arbitration</b>: modules that move items call {@link #tryAcquireClicks(Object, int)} each tick; only
 *     the highest priority requester of the tick may click, and the number of clicks per tick is bounded, so
 *     AutoTotem, AutoArmor, InventoryManager and ChestStealer never interleave clicks.</li>
 * </ul>
 */
public final class InventoryManager {
	public static final int HOTBAR_START = 0;
	public static final int HOTBAR_END = 8;
	public static final int MAIN_START = 9;
	public static final int MAIN_END = 35;
	public static final int ARMOR_START = 36;
	public static final int OFFHAND = Inventory.SLOT_OFFHAND;

	/** Conventional priorities for hotbar and click arbitration. */
	public static final class Priority {
		public static final int LOW = 0;
		public static final int TOOL = 20;
		public static final int WEAPON = 30;
		public static final int BUILD = 40;
		public static final int CONSUME = 60;
		public static final int HEAL = 80;
		public static final int TOTEM = 100;

		private Priority() {
		}
	}

	private final Minecraft mc = Minecraft.getInstance();

	private Object slotOwner;
	private int slotOwnerPriority;
	private int slotToRestore = -1;

	private Object clickOwner;
	private int clickOwnerPriority = Integer.MIN_VALUE;
	private int clicksThisTick;
	private int maxClicksPerTick = 4;

	public void init() {
		BirdWare.get().events().subscribe(this);
	}

	// ------------------------------------------------------------------------------------------------ queries

	private Inventory inv() {
		return mc.player.getInventory();
	}

	public ItemStack getStack(int index) {
		if (mc.player == null) return ItemStack.EMPTY;
		return inv().getItem(index);
	}

	public int getSelectedSlot() {
		return mc.player == null ? 0 : inv().getSelectedSlot();
	}

	public ItemStack getMainHand() {
		return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
	}

	public ItemStack getOffhand() {
		return mc.player == null ? ItemStack.EMPTY : mc.player.getOffhandItem();
	}

	/** Inventory index of the armor slot ({@link EquipmentSlot#FEET} → 36 ... HEAD → 39). */
	public static int armorIndex(EquipmentSlot slot) {
		return slot.getIndex(ARMOR_START);
	}

	/** First hotbar slot matching, or -1. */
	public int findHotbar(Predicate<ItemStack> filter) {
		return find(filter, HOTBAR_START, HOTBAR_END);
	}

	/** First slot matching in the main inventory (9-35), or -1. */
	public int findMain(Predicate<ItemStack> filter) {
		return find(filter, MAIN_START, MAIN_END);
	}

	/** First matching slot, hotbar first then main inventory, or -1. */
	public int findAny(Predicate<ItemStack> filter) {
		int slot = findHotbar(filter);
		return slot != -1 ? slot : findMain(filter);
	}

	public int find(Predicate<ItemStack> filter, int from, int to) {
		if (mc.player == null) return -1;
		for (int i = from; i <= to; i++) {
			ItemStack stack = inv().getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) return i;
		}
		return -1;
	}

	/** Slot in [from, to] with the highest positive score, or -1. Ties prefer the currently selected slot. */
	public int findBest(ToDoubleFunction<ItemStack> score, int from, int to) {
		if (mc.player == null) return -1;
		int best = -1;
		double bestScore = 0;
		int selected = inv().getSelectedSlot();
		for (int i = from; i <= to; i++) {
			ItemStack stack = inv().getItem(i);
			if (stack.isEmpty()) continue;
			double s = score.applyAsDouble(stack);
			if (s > bestScore || (s == bestScore && s > 0 && i == selected)) {
				bestScore = s;
				best = i;
			}
		}
		return best;
	}

	public int findBestHotbar(ToDoubleFunction<ItemStack> score) {
		return findBest(score, HOTBAR_START, HOTBAR_END);
	}

	/** Total item count matching across hotbar, main inventory and offhand. */
	public int count(Predicate<ItemStack> filter) {
		if (mc.player == null) return 0;
		int total = 0;
		for (int i = 0; i <= MAIN_END; i++) {
			ItemStack stack = inv().getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) total += stack.getCount();
		}
		ItemStack off = inv().getItem(OFFHAND);
		if (!off.isEmpty() && filter.test(off)) total += off.getCount();
		return total;
	}

	/** First empty slot in hotbar+main (hotbar preferred), or -1. */
	public int findEmpty() {
		if (mc.player == null) return -1;
		for (int i = 0; i <= MAIN_END; i++) {
			if (inv().getItem(i).isEmpty()) return i;
		}
		return -1;
	}

	public int emptySlotCount() {
		if (mc.player == null) return 0;
		int n = 0;
		for (int i = 0; i <= MAIN_END; i++) {
			if (inv().getItem(i).isEmpty()) n++;
		}
		return n;
	}

	public static boolean isHotbar(int index) {
		return index >= HOTBAR_START && index <= HOTBAR_END;
	}

	// ------------------------------------------------------------------------------------------------ hotbar selection

	/** Selects a hotbar slot and synchronises it with the server immediately. */
	public void select(int hotbarSlot) {
		if (mc.player == null || !isHotbar(hotbarSlot)) return;
		if (inv().getSelectedSlot() != hotbarSlot) inv().setSelectedSlot(hotbarSlot);
		sync();
	}

	/** Sends the selected slot to the server now (normally deferred until the next interaction). */
	public void sync() {
		if (mc.gameMode != null) ((MultiPlayerGameModeAccessor) mc.gameMode).birdware$syncSelectedSlot();
	}

	/**
	 * Requests ownership of the selected hotbar slot. Returns true if the slot is now selected for this owner.
	 * A higher priority request preempts the current owner. Call every tick while the slot is needed, then
	 * {@link #releaseSlot(Object)}.
	 */
	public boolean requestSlot(Object owner, int hotbarSlot, int priority) {
		if (mc.player == null || !isHotbar(hotbarSlot)) return false;
		if (slotOwner != null && slotOwner != owner && slotOwnerPriority > priority) return false;
		if (slotOwner == null) slotToRestore = inv().getSelectedSlot();
		slotOwner = owner;
		slotOwnerPriority = priority;
		select(hotbarSlot);
		return true;
	}

	/** Releases slot ownership and restores the slot that was selected before (if {@code restore}). */
	public void releaseSlot(Object owner, boolean restore) {
		if (slotOwner != owner) return;
		slotOwner = null;
		slotOwnerPriority = Integer.MIN_VALUE;
		if (restore && slotToRestore >= 0 && mc.player != null) select(slotToRestore);
		slotToRestore = -1;
	}

	public void releaseSlot(Object owner) {
		releaseSlot(owner, true);
	}

	public boolean isSlotOwner(Object owner) {
		return slotOwner == owner;
	}

	/** True if some module currently owns the hotbar selection. */
	public boolean isSlotLocked() {
		return slotOwner != null;
	}

	/**
	 * Silently switches to {@code hotbarSlot}, runs the action and switches back within the same tick (the server
	 * sees the action performed with that item; the client view stays on the original slot).
	 */
	public void withSlot(int hotbarSlot, Runnable action) {
		if (mc.player == null || !isHotbar(hotbarSlot)) return;
		int previous = inv().getSelectedSlot();
		if (previous == hotbarSlot) {
			action.run();
			return;
		}
		select(hotbarSlot);
		try {
			action.run();
		} finally {
			select(previous);
		}
	}

	// ------------------------------------------------------------------------------------------------ clicking

	public void setMaxClicksPerTick(int max) {
		this.maxClicksPerTick = Math.max(1, max);
	}

	/**
	 * Claims the right to perform inventory clicks this tick. Returns false if a higher priority module already
	 * claimed this tick or the click budget is exhausted.
	 */
	public boolean tryAcquireClicks(Object owner, int priority) {
		if (clickOwner != null && clickOwner != owner && clickOwnerPriority >= priority) return false;
		clickOwner = owner;
		clickOwnerPriority = priority;
		return clicksThisTick < maxClicksPerTick;
	}

	public int remainingClicks() {
		return Math.max(0, maxClicksPerTick - clicksThisTick);
	}

	/** Menu slot id of an inventory index in the currently open container menu, or -1 if not present. */
	public int toMenuSlot(int index) {
		if (mc.player == null) return -1;
		AbstractContainerMenu menu = mc.player.containerMenu;
		Inventory inventory = inv();
		for (Slot slot : menu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == index) return slot.index;
		}
		return -1;
	}

	/** Raw click on a menu slot id of the open menu. */
	public void clickMenuSlot(int menuSlot, int button, ClickType type) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || menuSlot < 0) return;
		mc.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, menuSlot, button, type, player);
		clicksThisTick++;
	}

	/** Swaps an inventory slot with a hotbar slot (number-key swap). Works with any container open. */
	public boolean swapToHotbar(int index, int hotbarSlot) {
		int menuSlot = toMenuSlot(index);
		if (menuSlot < 0 || !isHotbar(hotbarSlot)) return false;
		if (index == hotbarSlot) return true;
		clickMenuSlot(menuSlot, hotbarSlot, ClickType.SWAP);
		return true;
	}

	/** Swaps an inventory slot with the offhand (F-key swap: SWAP with button 40). */
	public boolean swapToOffhand(int index) {
		int menuSlot = toMenuSlot(index);
		if (menuSlot < 0) return false;
		if (index == OFFHAND) return true;
		clickMenuSlot(menuSlot, OFFHAND, ClickType.SWAP);
		return true;
	}

	/** Shift-click: moves the stack between player inventory and the open container (or armor/hotbar). */
	public boolean quickMove(int index) {
		int menuSlot = toMenuSlot(index);
		if (menuSlot < 0) return false;
		clickMenuSlot(menuSlot, 0, ClickType.QUICK_MOVE);
		return true;
	}

	/** Shift-click on a container (non player) menu slot id. */
	public void quickMoveMenuSlot(int menuSlot) {
		clickMenuSlot(menuSlot, 0, ClickType.QUICK_MOVE);
	}

	/** Drops one item ({@code all=false}) or the whole stack. */
	public boolean drop(int index, boolean all) {
		int menuSlot = toMenuSlot(index);
		if (menuSlot < 0) return false;
		clickMenuSlot(menuSlot, all ? 1 : 0, ClickType.THROW);
		return true;
	}

	/**
	 * Moves the stack from {@code from} to {@code to} with pickup clicks (pick up, put down, and put back whatever
	 * was in the target). Uses up to three clicks.
	 */
	public boolean move(int from, int to) {
		int a = toMenuSlot(from);
		int b = toMenuSlot(to);
		if (a < 0 || b < 0 || a == b) return false;
		boolean targetOccupied = !getStack(to).isEmpty();
		clickMenuSlot(a, 0, ClickType.PICKUP);
		clickMenuSlot(b, 0, ClickType.PICKUP);
		if (targetOccupied) clickMenuSlot(a, 0, ClickType.PICKUP);
		return true;
	}

	/** Swaps main hand and offhand items like the F key (no inventory screen needed). */
	public void swapHands() {
		if (mc.player == null || mc.getConnection() == null) return;
		mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
	}

	/** True when no item is held on the cursor (safe to start a click sequence). */
	public boolean isCursorEmpty() {
		return mc.player != null && mc.player.containerMenu.getCarried().isEmpty();
	}

	/** True when the player inventory (or no screen) is the active menu, i.e. armor/offhand slots are clickable. */
	public boolean isPlayerMenuActive() {
		return mc.player != null && mc.player.containerMenu == mc.player.inventoryMenu;
	}

	// ------------------------------------------------------------------------------------------------ lifecycle

	@Subscribe(priority = EventPriority.LOWEST)
	private void onTickEnd(TickEvent.Post event) {
		clickOwner = null;
		clickOwnerPriority = Integer.MIN_VALUE;
		clicksThisTick = 0;
	}

	@Subscribe
	private void onWorldChange(WorldChangeEvent event) {
		slotOwner = null;
		slotOwnerPriority = Integer.MIN_VALUE;
		slotToRestore = -1;
		clickOwner = null;
		clicksThisTick = 0;
	}
}
