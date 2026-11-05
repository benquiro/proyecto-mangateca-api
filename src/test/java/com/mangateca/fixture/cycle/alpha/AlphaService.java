package com.mangateca.fixture.cycle.alpha;

import com.mangateca.fixture.cycle.beta.BetaService;

public class AlphaService {

	public BetaService beta() {
		return new BetaService();
	}

}
