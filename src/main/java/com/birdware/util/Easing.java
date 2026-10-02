package com.birdware.util;

/** Standard easing curves. Input and output are in [0, 1]. */
public enum Easing {
	LINEAR {
		@Override
		public double apply(double t) {
			return t;
		}
	},
	QUAD_OUT {
		@Override
		public double apply(double t) {
			return 1 - (1 - t) * (1 - t);
		}
	},
	CUBIC_OUT {
		@Override
		public double apply(double t) {
			double u = 1 - t;
			return 1 - u * u * u;
		}
	},
	CUBIC_IN_OUT {
		@Override
		public double apply(double t) {
			return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
		}
	},
	QUART_OUT {
		@Override
		public double apply(double t) {
			double u = 1 - t;
			return 1 - u * u * u * u;
		}
	},
	EXPO_OUT {
		@Override
		public double apply(double t) {
			return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
		}
	},
	BACK_OUT {
		@Override
		public double apply(double t) {
			double c1 = 1.70158;
			double c3 = c1 + 1;
			return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
		}
	},
	SINE_IN_OUT {
		@Override
		public double apply(double t) {
			return -(Math.cos(Math.PI * t) - 1) / 2;
		}
	};

	public abstract double apply(double t);

	public double clamped(double t) {
		return apply(t <= 0 ? 0 : Math.min(t, 1));
	}
}
