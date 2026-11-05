package com.mangateca.fixture.cycle.beta;

import com.mangateca.fixture.cycle.alpha.AlphaService;

public class BetaService {

	public AlphaService alpha() {
		return new AlphaService();
	}

}
