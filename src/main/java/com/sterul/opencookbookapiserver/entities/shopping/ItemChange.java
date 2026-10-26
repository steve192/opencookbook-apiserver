package com.sterul.opencookbookapiserver.entities.shopping;

/** What a person did to an item, as the list's history records it. */
public enum ItemChange {
    /** Put on the list, more of it asked for, or a suggestion taken. */
    ADDED,
    /** Put back from recently bought. */
    RESTORED,
    BOUGHT,
    /** Deleted from the list; moving to another list is a removal here. */
    REMOVED,
    RENAMED,
    SPEC_CHANGED,
    AISLE_CHANGED,
    PRIORITIZED,
    UNPRIORITIZED
}
