package io.hotmoka.examples.constructoronthis;

import java.math.BigInteger;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.View;

public class Bridge extends Contract {
	private Sub sub;
	private BigInteger initialBalance;

	public @FromContract @Payable void foo(int amount) {
		initialBalance = balance();
		sub = new Sub(amount);
	}

	public @View BigInteger getBalance() {
		return balance();
	}

	public @View BigInteger getInitialBalance() {
		return initialBalance;
	}

	public @View BigInteger getBalanceOfSub() {
		return sub.getBalance();
	}
}