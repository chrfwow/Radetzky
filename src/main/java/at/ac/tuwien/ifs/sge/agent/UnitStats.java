package at.ac.tuwien.ifs.sge.agent;

public class UnitStats {
    public static final int[] hpOfType = new int[] {0, 10, 4, 30}; // index 0 is unused, because type id starts at 1
    public static final int[] fovOfType = new int[] {0, 1, 2, 1}; // index 0 is unused, because type id starts at 1
    public static final int[] costOfType = new int[] {0, 10, 10, 20}; // index 0 is unused, because type id starts at 1
    public static final float[] damagePerSecondOfType = new float[] {0f, 1.5f * .8f, .5f * 1f, 3f * .6f}; // index 0 is unused, because type id starts at 1
    public static final float[] speedOfType = new float[] {0f, .4f, .8f, .6f}; // index 0 is unused, because type id starts at 1
    public static final float maxUnitCost = 20f;
    public static final float maxHp = 30f;
}
