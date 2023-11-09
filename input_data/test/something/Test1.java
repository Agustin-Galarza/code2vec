public class Test1 {
    public String firstMethod(String arg) {
        int x = 1;
        int y = 2;
        String msg = String.format("This is a message: %s", arg);
        msg = msg + String.format("\nWith a result: %d", x + y);

        return msg;
    }

    public void secondMethod(int x, int y) {
        int result = x + y;
        System.out.println(result);
    }

    public int zeroMethod() {
        return 1;
    }
}
