package com.nexuscraft.nexusdungeons;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A small, self-contained, in-memory party system -- invite/accept/leave/disband, mutual
 * membership, at most one party per player. Deliberately live-session-only (no persistence
 * across a restart, same as who's standing where): a dungeon run is something players are doing
 * together right now, not a standing guild. Kept independent of NexusExpeditions' own party
 * concept rather than guessing at an unverified cross-plugin API -- see README for why a real
 * soft integration with it is a natural future step, not this version's.
 */
final class DungeonParty {

    private final Map<UUID, UUID> partyOfPlayer = new LinkedHashMap<>(); // member -> leader
    private final Map<UUID, Set<UUID>> membersOfLeader = new LinkedHashMap<>(); // leader -> members (incl. leader)
    private final Map<UUID, UUID> pendingInvites = new LinkedHashMap<>(); // invited player -> leader

    /** Every player in the same party as this one, always including themself -- a solo player's
     *  "party" is just themself, so callers never need a special no-party case. */
    Set<UUID> partyOf(UUID playerId) {
        UUID leader = partyOfPlayer.get(playerId);
        if (leader == null) {
            return Set.of(playerId);
        }
        return Set.copyOf(membersOfLeader.getOrDefault(leader, Set.of(playerId)));
    }

    boolean isInParty(UUID playerId) {
        return partyOfPlayer.containsKey(playerId);
    }

    UUID leaderOf(UUID playerId) {
        return partyOfPlayer.getOrDefault(playerId, playerId);
    }

    void invite(UUID leaderId, UUID targetId) {
        pendingInvites.put(targetId, leaderId);
    }

    /** Returns the inviting leader's id on success, or null if there was no pending invite from
     *  anyone for this player to accept. */
    UUID acceptInvite(UUID playerId) {
        UUID leaderId = pendingInvites.remove(playerId);
        if (leaderId == null) {
            return null;
        }
        UUID actualLeader = partyOfPlayer.getOrDefault(leaderId, leaderId);
        membersOfLeader.computeIfAbsent(actualLeader, k -> {
            Set<UUID> set = new LinkedHashSet<>();
            set.add(actualLeader);
            return set;
        }).add(playerId);
        partyOfPlayer.put(actualLeader, actualLeader);
        partyOfPlayer.put(playerId, actualLeader);
        return actualLeader;
    }

    /** Removes this player from whatever party they're in -- if they were the leader, the whole
     *  party disbands (kept simple on purpose: no automatic leader handoff). */
    void leave(UUID playerId) {
        UUID leader = partyOfPlayer.remove(playerId);
        if (leader == null) {
            return;
        }
        if (leader.equals(playerId)) {
            disband(leader);
            return;
        }
        Set<UUID> members = membersOfLeader.get(leader);
        if (members != null) {
            members.remove(playerId);
        }
    }

    void disband(UUID leaderId) {
        Set<UUID> members = membersOfLeader.remove(leaderId);
        if (members != null) {
            for (UUID member : members) {
                partyOfPlayer.remove(member);
            }
        }
        partyOfPlayer.remove(leaderId);
    }
}
