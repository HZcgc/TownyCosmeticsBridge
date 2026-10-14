package com.leonardobishop.quests.bukkit.api.event;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
/** Controlled finish-event API boundary; never included in the release JAR. */
public final class PlayerFinishQuestEvent extends PlayerEvent {
    private static final HandlerList HANDLERS=new HandlerList();
    private final QuestProgress progress;
    public PlayerFinishQuestEvent(Player player,QuestProgress progress){super(player);this.progress=progress;}
    public QuestProgress getQuestProgress(){return progress;}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
