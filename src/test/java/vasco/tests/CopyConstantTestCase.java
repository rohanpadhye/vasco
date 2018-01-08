package vasco.tests;

public class CopyConstantTestCase {
	public static void main(String... args) {
		Inner in = new Inner(8);
		int x = in.sq();
		int y = in.val();
		int z = in.foo(8, 8);
		System.out.println(x+y+z);
	}
	
	private static class Inner {
		int data;
		
		Inner(int val) {
			this.data = val;
		}
		
		public int sq() {
			return data*data;
		}
		
		public int val() {
			return this.data;
		}
		
		public int foo(int a, int b) {
			int x = a;
			int y = b;
			int z;
			if (a < 5) {
				z = x;
			} else {
				z = y;
			}
			return z;
		}
	}
}
