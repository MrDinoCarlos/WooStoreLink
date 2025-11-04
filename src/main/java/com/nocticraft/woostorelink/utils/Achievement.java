package com.nocticraft.woostorelink.utils;

import java.util.List;

/**
 * Immutable achievement definition loaded from config.
 */
public class Achievement {
    private final String id;
    private final int count;
    private final String title;
    private final String description;
    private final boolean broadcast;
    private final List<String> itemRewards;
    private final List<String> commandRewards;

    public Achievement(String id, int count, String title, String description, boolean broadcast,
                       List<String> itemRewards, List<String> commandRewards) {
        this.id = id;
        this.count = count;
        this.title = title;
        this.description = description;
        this.broadcast = broadcast;
        this.itemRewards = itemRewards;
        this.commandRewards = commandRewards;
    }

    public String getId() { return id; }
    public int getCount() { return count; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isBroadcast() { return broadcast; }
    public List<String> getItemRewards() { return itemRewards; }
    public List<String> getCommandRewards() { return commandRewards; }
}
