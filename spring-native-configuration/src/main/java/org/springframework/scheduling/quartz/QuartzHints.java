/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.scheduling.quartz;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.utils.DirtyFlagMap;
import org.quartz.utils.StringKeyDirtyFlagMap;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.FieldHint;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.SerializationHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.AccessDescriptor;
import org.springframework.nativex.type.HintDeclaration;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.type.Type;
import org.springframework.nativex.type.TypeSystem;

/**
 * Supports running scheduled Quartz jobs with Spring
 * 
 * @author Josh Long
 */


@NativeHint(trigger = Job.class,
	types = @TypeHint(
		types = CopyOnWriteArrayList.class,
		fields = {
			@FieldHint(
				allowWrite = true,
				allowUnsafeAccess = true,
				name = "lock"
			)
		},
		access = AccessBits.ALL
	),
	serializables = @SerializationHint(
			types = {
					DirtyFlagMap.class, Long.class, Integer.class,
					Boolean.class, Double.class, Float.class,
					Date.class, String.class, StringKeyDirtyFlagMap.class,
					JobDataMap.class, HashMap.class
				}
			),
	resources=@ResourceHint(
			patterns = {
					"org/quartz/impl/jdbcjobstore/tables_.*.sql$"
				}
			)
)
@NativeHint(trigger = Job.class,
	types = @TypeHint(
		types = HashMap.class,
		fields = {
			@FieldHint(
				allowUnsafeAccess = true, name = "threshold"
			),
			@FieldHint(
				allowUnsafeAccess = true, name = "loadFactor"
			)
		},
		access = AccessBits.ALL
	)
)
@NativeHint(
	trigger = org.quartz.utils.DirtyFlagMap.class,
	types = {
		@TypeHint(types = java.util.HashMap.class),
		@TypeHint(
			access = AccessBits.ALL,
			types = {
				org.quartz.impl.triggers.SimpleTriggerImpl.class,
				org.quartz.utils.DirtyFlagMap.class,
				org.quartz.impl.jdbcjobstore.StdJDBCDelegate.class,
				org.quartz.simpl.RAMJobStore.class,
				LocalDataSourceJobStore.class,
				SimpleThreadPool.class,
				ResourceLoaderClassLoadHelper.class,
				LocalTaskExecutorThreadPool.class,
				StdSchedulerFactory.class,
			}
		)}
)
public class QuartzHints implements NativeConfiguration {

	@Override
	public List<HintDeclaration> computeHints(TypeSystem typeSystem) {
		// Add reflection configuration for the subtypes of org.quartz.Job
		Type job = typeSystem.resolve("org/quartz/Job", true);
		if (job != null) {
			List<Type> jobSubtypes = job.getSubtypes();
			HintDeclaration hintDeclaration = new HintDeclaration();
			for (Type jobSubtype: jobSubtypes) {
				hintDeclaration.addDependantType(jobSubtype.getDottedName(), new AccessDescriptor(AccessBits.FULL_REFLECTION));
			}
			return Collections.singletonList(hintDeclaration);
		}
		return Collections.emptyList();
	}
}
