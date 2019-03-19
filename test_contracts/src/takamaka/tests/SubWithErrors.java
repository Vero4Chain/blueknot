package takamaka.tests;

import takamaka.lang.Entry;
import takamaka.lang.Payable;

public class SubWithErrors extends Super {

	public @Entry @Payable SubWithErrors(int amount) {
		super(amount > 10 ? 13 : 17); // ok
	}

	public SubWithErrors(boolean b) {
		super(b); // exception at run time
	}

	public @Entry SubWithErrors() {
		super(true); // exception at run time
	}

	@Override
	public void m1() { // this is implicitly @Entry by inheritance
		super.m1(); // exception at run time
		System.out.println("Sub.m1");
	}

	@Override @Entry // this cannot become @Entry: error at compile time
	public void m2() {
		super.m2();
	}

	@Override
	public void m3() { // this is implicitly @Entry
		System.out.println("Sub.m3 with caller " + caller());
	}

	@Override
	public void m4(int amount) { // this is implicitly @Payable @Entry
		System.out.println("Sub.m4 receives " + amount + " coins from " + caller());
	}

	public void m5() {
		super.m2(); // ok
		caller(); // error at compile time
		SubWithErrors c = new SubWithErrors();
		c.caller(); // error at compile time
	}

	public static @Entry void m6() {} // error at compile time

	public @Payable void m7() {} // error at compile time
}