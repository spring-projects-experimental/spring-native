/*
 * Copyright 2019-2021 the original author or authors.
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

package org.springframework.boot.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.pattern.DateConverter;
import ch.qos.logback.classic.pattern.LevelConverter;
import ch.qos.logback.classic.pattern.LineSeparatorConverter;
import ch.qos.logback.classic.pattern.LoggerConverter;
import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.pattern.ThreadConverter;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.DateTokenConverter;
import ch.qos.logback.core.rolling.helper.IntegerTokenConverter;
import ch.qos.logback.core.util.FileSize;
import org.springframework.boot.logging.logback.ColorConverter;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.NativeConfiguration;

// TODO Send a PR to Logback to remove reflection usage in ch.qos.logback.classic.PatternLayout
// TODO Initialize ch.qos.logback.classic.PatternLayout at build time?
@NativeHint(types = {
        @TypeHint(types = {
                DateConverter.class,
                LevelConverter.class,
                LoggerConverter.class,
                MessageConverter.class,
                LineSeparatorConverter.class,
                ThreadConverter.class,
                MDCConverter.class,
                ColorConverter.class,
                WhitespaceThrowableProxyConverter.class,
                ExtendedWhitespaceThrowableProxyConverter.class,
                IntegerTokenConverter.class,
                DateTokenConverter.class
        },
                typeNames = "org.codehaus.janino.ScriptEvaluator"), // in case janino is present
        @TypeHint(types = {
                PatternLayoutEncoder.class,
                ConsoleAppender.class,
                RollingFileAppender.class,
                FixedWindowRollingPolicy.class,
                SizeBasedTriggeringPolicy.class,
                TimeBasedRollingPolicy.class,
                SizeAndTimeBasedRollingPolicy.class,
                FileSize.class
        }, access = AccessBits.PUBLIC_CONSTRUCTORS | AccessBits.PUBLIC_METHODS)},
        resources = {
                @ResourceHint(patterns = "org/springframework/boot/logging/logback/defaults.xml"),
                @ResourceHint(patterns = "org/springframework/boot/logging/logback/console-appender.xml"),
                @ResourceHint(patterns = "org/springframework/boot/logging/logback/file-appender.xml")
        })
public class LogbackHints implements NativeConfiguration {
}
