package org.springframework.context.bootstrap.generator.sample.generic;

public class GenericWildcardComponent {

	private final Repository<?> repository;

	public GenericWildcardComponent(Repository<?> repository) {
		this.repository = repository;
	}

}
