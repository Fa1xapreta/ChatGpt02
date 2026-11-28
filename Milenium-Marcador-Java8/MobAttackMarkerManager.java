package net.sf.l2j.gameserver.instancemanager;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ThreadPoolManager;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.TutorialShowHtml;

public final class MobAttackMarkerManager
{
	private static final Logger LOGGER = Logger.getLogger(MobAttackMarkerManager.class.getName());
	private static final int MAX_MARKERS = 32;
	private static final int MAX_DISTANCE = 2000;
	private static final long HIT_GRACE = TimeUnit.MILLISECONDS.toNanos(1500);
	private static final String PREFIX = "MOB_ATTACK_MARK|";
	private static final Map<L2PcInstance, Map<L2MonsterInstance, Long>> MARKERS = new HashMap<>();
	private static volatile Set<Integer> npcIds = Collections.emptySet();
	private static boolean scheduled;

	private MobAttackMarkerManager()
	{
	}

	public static void configure(String configuredIds)
	{
		Set<Integer> ids = new HashSet<>();
		for (String field : configuredIds.split(","))
		{
			String value = field.trim();
			if (value.isEmpty())
				continue;
			try
			{
				int id = Integer.parseInt(value);
				if (id > 0)
					ids.add(id);
				else
					LOGGER.warning("MobAttackMarkerNpcIds: invalid NPC ID " + value);
			}
			catch (NumberFormatException e)
			{
				LOGGER.warning("MobAttackMarkerNpcIds: invalid NPC ID " + value);
			}
		}
		npcIds = Collections.unmodifiableSet(ids);
	}

	public static void onHit(L2PcInstance player, L2MonsterInstance attacker)
	{
		if (!npcIds.contains(attacker.getNpcId()) || player.isAlikeDead())
			return;

		synchronized (MARKERS)
		{
			Map<L2MonsterInstance, Long> attackers = MARKERS.computeIfAbsent(player, key -> new LinkedHashMap<>());
			if (attackers.size() < MAX_MARKERS || attackers.containsKey(attacker))
				attackers.put(attacker, System.nanoTime());

			if (!scheduled)
				scheduled = ThreadPoolManager.getInstance().scheduleGeneral(MobAttackMarkerManager::publish, 250) != null;
			if (!scheduled)
				MARKERS.clear();
		}
	}

	private static void publish()
	{
		synchronized (MARKERS)
		{
			long now = System.nanoTime();
			Iterator<Map.Entry<L2PcInstance, Map<L2MonsterInstance, Long>>> players = MARKERS.entrySet().iterator();
			while (players.hasNext())
			{
				Map.Entry<L2PcInstance, Map<L2MonsterInstance, Long>> entry = players.next();
				L2PcInstance player = entry.getKey();
				if (!player.isOnline())
				{
					players.remove();
					continue;
				}

				Map<L2MonsterInstance, Long> attackers = entry.getValue();
				attackers.entrySet().removeIf(hit -> !shouldMark(player, hit.getKey(), now - hit.getValue()));
				StringJoiner ids = new StringJoiner(",");
				for (L2MonsterInstance attacker : attackers.keySet())
					ids.add(Integer.toString(attacker.getObjectId()));
				player.sendPacket(new TutorialShowHtml(PREFIX + ids));
				if (attackers.isEmpty())
					players.remove();
			}
			scheduled = !MARKERS.isEmpty() && ThreadPoolManager.getInstance().scheduleGeneral(MobAttackMarkerManager::publish, 500) != null;
			if (!scheduled)
				MARKERS.clear();
		}
	}

	private static boolean shouldMark(L2PcInstance player, L2MonsterInstance attacker, long sinceHit)
	{
		if (player.isAlikeDead() || attacker.isAlikeDead() || !npcIds.contains(attacker.getNpcId())
			|| player.getInstanceId() != attacker.getInstanceId() || !player.isInsideRadius(attacker, MAX_DISTANCE, true, false))
			return false;

		if (sinceHit < HIT_GRACE)
			return true;
		if (!attacker.hasAI() || attacker.getAI().getTarget() != player)
			return false;

		CtrlIntention intention = attacker.getAI().getIntention();
		return intention == CtrlIntention.ATTACK || intention == CtrlIntention.CAST;
	}
}
