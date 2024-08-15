/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.strictmode;

import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_ESCAPED;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.auto.service.AutoService;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.AndroidInstrumentation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RequiresApi(api = Build.VERSION_CODES.P) // TODO: Hadi – Handle compatibility with older versions.
@AutoService(AndroidInstrumentation.class) // TODO: Hadi – Make optional (via a marker interface?)
public class StrictModeViolationInstrumentation implements AndroidInstrumentation {

    private final Executor executor =
            Executors.newSingleThreadExecutor(); // TODO: Hadi – SDK-level executor?

    @Override
    public void install(
            @NonNull Application application, @NonNull OpenTelemetryRum openTelemetryRum) {
        OpenTelemetrySdk openTelemetrySdk = (OpenTelemetrySdk) openTelemetryRum.getOpenTelemetry();
        StrictMode.OnThreadViolationListener threadViolationListener =
                new ThreadViolationListener(openTelemetrySdk.getSdkLoggerProvider());
        StrictMode.ThreadPolicy threadPolicy =
                new StrictMode.ThreadPolicy.Builder()
                        .detectAll()
                        .penaltyListener(executor, threadViolationListener)
                        .build();
        StrictMode.setThreadPolicy(threadPolicy);
    }

    static class ThreadViolationListener implements StrictMode.OnThreadViolationListener {
        private final Logger logger;

        public ThreadViolationListener(SdkLoggerProvider sdkLoggerProvider) {
            this.logger =
                    sdkLoggerProvider
                            .loggerBuilder("io.opentelemetry.android.strictmode.thread")
                            .build();
        }

        @Override
        public void onThreadViolation(Violation violation) {
            AttributesBuilder attributesBuilder =
                    Attributes.builder()
                            .put(EXCEPTION_ESCAPED, false)
                            .put(EXCEPTION_MESSAGE, violation.getMessage())
                            .put(EXCEPTION_STACKTRACE, stackTraceToString(violation))
                            .put(EXCEPTION_TYPE, violation.getClass().getName());

            logger.logRecordBuilder().setAllAttributes(attributesBuilder.build()).emit();
        }

        private String stackTraceToString(Throwable throwable) {
            StringWriter sw = new StringWriter(256);
            PrintWriter pw = new PrintWriter(sw);

            throwable.printStackTrace(pw);
            pw.flush();

            return sw.toString();
        }
    }
}
