/*
 * Copyright 2016 M-Way Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.relution.jenkins.scmsqs.threading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutorService;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitorScheduler;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;


public class SQSQueueMonitorSchedulerImplTest {

    private static final String      UUID_A = "uuid-a";
    private static final String      UUID_B = "uuid-b";

    @Mock
    private ExecutorService          executor;

    @Mock
    private SQSQueueProvider         provider;

    @Mock
    private SQSFactory               factory;

    @Mock
    private SQSQueueMonitor          monitorA;

    @Mock
    private SQSQueueMonitor          monitorB;

    @Mock
    private SQSQueueListener         listenerA1;

    @Mock
    private SQSQueueListener         listenerA2;

    @Mock
    private SQSQueueListener         listenerB1;

    @Mock
    private SQSQueue                 queueA;

    @Mock
    private SQSQueue                 queueB;

    private SQSQueueMonitorScheduler scheduler;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(this.factory.createMonitor(this.executor, this.queueA)).thenReturn(this.monitorA);
        Mockito.when(this.factory.createMonitor(this.executor, this.queueB)).thenReturn(this.monitorB);

        Mockito.when(this.listenerA1.getQueueUuid()).thenReturn(UUID_A);
        Mockito.when(this.listenerA2.getQueueUuid()).thenReturn(UUID_A);
        Mockito.when(this.listenerB1.getQueueUuid()).thenReturn(UUID_B);

        Mockito.when(this.queueA.getUuid()).thenReturn(UUID_A);
        Mockito.when(this.queueB.getUuid()).thenReturn(UUID_B);

        Mockito.when(this.provider.getSqsQueue(UUID_A)).thenReturn(this.queueA);
        Mockito.when(this.provider.getSqsQueue(UUID_B)).thenReturn(this.queueB);

        this.scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);
    }

    @Test
    public void shouldThrowIfRegisterNullListener() {
        assertThatThrownBy(new ThrowingCallable() {

            @Override
            public void call() throws Throwable {
                SQSQueueMonitorSchedulerImplTest.this.scheduler.register(null);
            }

        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotThrowIfUnregisterNullListener() {
        assertThat(this.scheduler.unregister(null)).isFalse();
    }

    @Test
    public void shouldNotThrowIfUnregisterUnknownListener() {
        final SQSQueueListener listener = Mockito.mock(SQSQueueListener.class);
        Mockito.when(listener.getQueueUuid()).thenReturn("unknown");

        final boolean result = this.scheduler.unregister(listener);

        assertThat(result).isFalse();
    }

    @Test
    public void shouldStartMonitorForFirstListener() {
        final boolean result = this.scheduler.register(this.listenerA1);

        assertThat(result).isTrue();
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_A);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);
    }

    @Test
    public void shouldUseSingleMonitorInstancePerQueue() {
        this.scheduler.register(this.listenerA1);
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_A);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        final boolean result = this.scheduler.register(this.listenerA2);

        assertThat(result).isTrue();
        Mockito.verify(this.provider, times(2)).getSqsQueue(UUID_A);
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldUseSeparateMonitorInstanceForEachQueue() {
        this.scheduler.register(this.listenerA1);
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_A);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        final boolean result = this.scheduler.register(this.listenerB1);

        assertThat(result).isTrue();
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_B);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorB).add(this.listenerB1);
        Mockito.verifyNoMoreInteractions(this.monitorA);
    }

    @Test
    public void shouldReuseMonitorInstaceForListenerOfSameQueue() {
        this.scheduler.register(this.listenerA1);
        this.scheduler.register(this.listenerB1);
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_A);
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_B);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA).add(this.listenerA1);
        Mockito.verify(this.monitorB).add(this.listenerB1);
        Mockito.verifyNoMoreInteractions(this.monitorA);

        final boolean result = this.scheduler.register(this.listenerA2);

        assertThat(result).isTrue();
        Mockito.verify(this.provider, times(2)).getSqsQueue(UUID_A);
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldNotCreateMonitorForUnknownQueue() {
        final SQSQueueListener listener = Mockito.mock(SQSQueueListener.class);
        Mockito.when(listener.getQueueUuid()).thenReturn("unknown");

        final boolean result = this.scheduler.register(listener);

        assertThat(result).isFalse();
        Mockito.verify(this.provider, times(1)).getSqsQueue("unknown");
        Mockito.verifyZeroInteractions(this.factory);
    }

    @Test
    public void shouldCreateNewMonitorAfterUnregisterLast() {
        this.scheduler.register(this.listenerA1);
        this.scheduler.unregister(this.listenerA1);
        Mockito.verify(this.provider, times(1)).getSqsQueue(UUID_A);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);
        Mockito.verify(this.monitorA).remove(this.listenerA1);

        final boolean result = this.scheduler.register(this.listenerA2);

        assertThat(result).isTrue();
        Mockito.verify(this.provider, times(2)).getSqsQueue(UUID_A);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldNotCreateNewMonitorIfMoreListenersOnUnregister() {
        this.scheduler.register(this.listenerA1);
        this.scheduler.register(this.listenerA2);
        this.scheduler.unregister(this.listenerA1);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA2);
        Mockito.verify(this.monitorA, times(1)).remove(this.listenerA1);

        final boolean result = this.scheduler.register(this.listenerA1);

        assertThat(result).isTrue();
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA, times(2)).add(this.listenerA1);
    }

    @Test
    public void shouldDoNothingOnConfigurationChangedIfUnchanged() {
        this.scheduler.register(this.listenerA1);
        this.scheduler.register(this.listenerB1);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);

        this.scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).isShutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldStopMonitorOnConfigurationChangedIfQueueRemoved() {
        this.scheduler.register(this.listenerA1);
        this.scheduler.register(this.listenerB1);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);
        Mockito.when(this.provider.getSqsQueue(UUID_B)).thenReturn(null);

        this.scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).shutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }
}