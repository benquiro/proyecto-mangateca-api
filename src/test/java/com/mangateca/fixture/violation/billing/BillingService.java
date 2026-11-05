package com.mangateca.fixture.violation.billing;

import com.mangateca.fixture.violation.orders.internal.OrderInternals;

public class BillingService {

	public String read() {
		return new OrderInternals().secret();
	}

}
