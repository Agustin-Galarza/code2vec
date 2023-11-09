public class Test2 {
    public String thirdMethod(String arg1, int arg2) {
        int x = 0;
        Float y = null;

        y = Float.MAX_VALUE;
        return String.format("%s: %d + %f = %f", arg1, x, y, (x + y) * arg2);
    }

    public float fourthMethod() {
        System.out.println("Heyeeeee");
        return 0.1;
    }

}
