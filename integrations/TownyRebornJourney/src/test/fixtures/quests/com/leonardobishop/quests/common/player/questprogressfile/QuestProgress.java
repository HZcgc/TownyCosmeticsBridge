package com.leonardobishop.quests.common.player.questprogressfile;
import java.util.UUID;
/** Minimal external API boundary, matching the inspected LMB Quests public getters. */
public final class QuestProgress {
    public String id; public UUID uuid; public long date; public boolean completed=true;
    public QuestProgress(UUID uuid,String id,long date){this.uuid=uuid;this.id=id;this.date=date;}
    public String getQuestId(){return id;}
    public UUID getPlayerUUID(){return uuid;}
    public long getCompletionDate(){return date;}
    public boolean isCompleted(){return completed;}
}
