package tests;

public class CallGraphTestCase {
	
	static class A {
		void foo() { bar(); }
		void bar() { }		
	}

	public static void main(String[] args) {
		A a1 = new A();
		a1.foo();
		
		A a2 = new A();
		a2.foo();

		a2.bar();

	}

}
