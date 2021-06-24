package org.springframework.context.origin;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;

/**
 * Strategy interface to analyze the structure of a {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 */
public interface BeanDefinitionOriginAnalyzer {

	/**
	 * Analyze a {@link BeanFactory} structure using the specified
	 * {@link BeanFactoryStructureAnalysis analysis}. Components that are detected should
	 * be flagged using {@link BeanFactoryStructureAnalysis#markAsProcessed(BeanDefinitionOrigin)}
	 * @param analysis the current analysis
	 */
	@Nullable
	void analyze(BeanFactoryStructureAnalysis analysis);

}
