package com.hnieacm.judge.ws;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调度权重回归：低权重保底不饥饿，权重越高本轮上限越高，且不超过 round budget。
 *
 * @author Codex
 */
class JudgeNodeTaskDispatcherWeightTest {

    private final JudgeNodeTaskDispatcher dispatcher = new JudgeNodeTaskDispatcher(null, null, null);

    @Test
    void lowWeightIsNeverStarved() {
        assertThat(dispatcher.weightedShare(1, 100)).isEqualTo(1);
        assertThat(dispatcher.weightedShare(1, 1000)).isEqualTo(1);
    }

    @Test
    void higherWeightGetsLargerShareButBounded() {
        assertThat(dispatcher.weightedShare(100, 100)).isEqualTo(8);
        assertThat(dispatcher.weightedShare(50, 100)).isEqualTo(4);
        assertThat(dispatcher.weightedShare(25, 100)).isEqualTo(2);
        assertThat(dispatcher.weightedShare(100, 1)).isEqualTo(8);
    }
}
