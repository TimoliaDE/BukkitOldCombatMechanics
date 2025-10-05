package kernitus.plugin.OldCombatMechanics.utilities;

import java.util.Objects;

public class IntTriple {

    private int x;
    private int y;
    private int z;

    public IntTriple(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean is(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        IntTriple intTriple = (IntTriple) o;
        return x == intTriple.x && y == intTriple.y && z == intTriple.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}