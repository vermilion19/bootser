package play;

public class Family {

    static void main() {
        test(new ChileOne());
        test(new ChildTwo());
    }

    static void test(Parent parent) {
        parent.print();
    }
}
