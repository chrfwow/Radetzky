package at.ac.tuwien.ifs.sge.agent;

public class UnitStats {
   public static final int[] hpOfType = new int[] {0, 10, 4, 30}; // index 0 is unused, because type id starts at 1
   public static final int[] fovOfType = new int[] {0, 1, 2, 1}; // index 0 is unused, because type id starts at 1
   public static final int[] costOfType = new int[] {0, 10, 10, 20}; // index 0 is unused, because type id starts at 1
   public static final double[] damagePerSecondOfType = new double[] {0, 1.5 * .8, .5 * 1, 3 * .6}; // index 0 is unused, because type id starts at 1
   public static final double[] speedOfType = new double[] {0, .4, .8, .6}; // index 0 is unused, because type id starts at 1
   public static final double maxUnitCost = 20;
}
