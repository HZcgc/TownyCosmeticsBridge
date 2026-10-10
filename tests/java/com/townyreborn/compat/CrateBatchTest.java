package com.townyreborn.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import su.nightexpress.excellentcrates.api.common.cooldown.CooldownMode;
import su.nightexpress.excellentcrates.api.common.cooldown.CooldownOptions;
import su.nightexpress.excellentcrates.api.common.quota.QuotaThreshold;
import su.nightexpress.excellentcrates.api.crate.Crate;
import su.nightexpress.excellentcrates.api.crate.component.CrateComponentKeys;
import su.nightexpress.excellentcrates.api.crate.pipeline.PipelineChain;
import su.nightexpress.excellentcrates.api.crate.pipeline.component.PipelineComponentKeys;
import su.nightexpress.excellentcrates.api.crate.pipeline.context.PipelineContext;
import su.nightexpress.excellentcrates.api.reward.Reward;
import su.nightexpress.excellentcrates.api.crate.pipeline.component.CrateRewardsPipelineComponent;
import su.nightexpress.excellentcrates.api.reward.dispatcher.RewardMessageDispatcher;
import su.nightexpress.excellentcrates.crates.batch.pipeline.component.DefaultCrateBatchPipelineComponent;
import su.nightexpress.excellentcrates.crates.cooldown.CrateCooldownService;
import su.nightexpress.excellentcrates.crates.cooldown.component.StandardCrateCooldownComponent;
import su.nightexpress.excellentcrates.crates.cooldown.quota.CrateCooldownQuotaProcessor;
import su.nightexpress.excellentcrates.reward.evaluation.RewardEvaluationService;
import su.nightexpress.excellentcrates.reward.evaluation.RollableReward;
import su.nightexpress.excellentcrates.reward.evaluation.pipeline.RewardEvaluationPipelineStage;

class CrateBatchTest {
    @Test void expiredOpeningCooldownStillCapsNativeBatchUntilDisabled() {
        Player player = mock(Player.class);
        Crate crate = mock(Crate.class);
        CrateCooldownService service = mock(CrateCooldownService.class);
        CooldownOptions individual = new CooldownOptions(true, CooldownMode.CUSTOM, 1);
        var component = new StandardCrateCooldownComponent(CooldownOptions.defaults(), individual);
        when(service.getCrateCooldowns(crate)).thenReturn(component);
        when(service.hasCooldownConfigured(crate)).thenCallRealMethod();
        // No active timestamp: even an expired one-second delay blocks batch selection.
        var processor = new CrateCooldownQuotaProcessor(service);
        assertEquals(1, processor.getThreshold(player, crate).threshold());
        individual.setEnabled(false);
        assertTrue(processor.getThreshold(player, crate).isAbsent());
        assertTrue(processor.test(player, crate).success());
    }

    @Test void nativeBatchRollKeepsOnePerPlayerSeasonalRewardsDistinct() {
        Player player = mock(Player.class);
        Crate crate = mock(Crate.class);
        PipelineContext context = mock(PipelineContext.class);
        PipelineChain chain = mock(PipelineChain.class);
        RewardEvaluationService evaluator = mock(RewardEvaluationService.class);
        RewardMessageDispatcher dispatcher = mock(RewardMessageDispatcher.class);
        var batch = new DefaultCrateBatchPipelineComponent(20);
        batch.setSelectedAmount(20);
        when(context.getComponent(PipelineComponentKeys.BATCH)).thenReturn(Optional.of(batch));
        when(crate.getComponent(CrateComponentKeys.SELECTABLE_REWARDS)).thenReturn(Optional.empty());
        when(evaluator.getRequiredRewards(crate)).thenReturn(1);
        Set<RollableReward> pool = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            Reward reward = mock(Reward.class);
            pool.add(new RollableReward(reward, 1, QuotaThreshold.of(1)));
        }
        when(evaluator.resolveAvailableRewards(crate, player)).thenReturn(pool);
        new RewardEvaluationPipelineStage(evaluator, dispatcher).intercept(player, crate, context, chain);
        var rewards = ArgumentCaptor.forClass(CrateRewardsPipelineComponent.class);
        verify(context).putComponent(eq(PipelineComponentKeys.REWARDS), rewards.capture());
        var result = rewards.getValue();
        assertEquals(20, result.getRequiredRewards());
        assertEquals(20, result.getTargetRewards().size());
        assertEquals(20, result.getTargetRewards().stream().map(r -> r.getRolled()).distinct().count());
        verify(chain).proceed(player, context);
        verify(chain, never()).abort();
    }
}
