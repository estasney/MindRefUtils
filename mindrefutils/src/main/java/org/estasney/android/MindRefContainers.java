package org.estasney.android;

public class MindRefContainers {
    public static class TupleTwo<X, Y> {
        private final X x;
        private final Y y;
        public TupleTwo(X x, Y y) {
            this.x = x;
            this.y = y;
        }

        public X getX() {
            return x;
        }

        public Y getY() {
            return y;
        }
    }


    public static class TupleThree<X, Y, Z> {
        private final X x;
        private final Y y;
        private final Z z;

        public TupleThree(X x, Y y, Z z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public X getX() {
            return x;
        }

        public Y getY() {
            return y;
        }

        public Z getZ() {
            return z;
        }
    }
}
