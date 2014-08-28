/**
 *  Copyright (C) 2002-2014   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.ColonyTradeItem;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FeatureContainer;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.InciteTradeItem;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.PlayerType;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitTradeItem;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.model.pathfinding.CostDeciders;
import net.sf.freecol.common.model.pathfinding.GoalDeciders;
import net.sf.freecol.common.networking.NetworkConstants;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.util.RandomChoice;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.ai.mission.BuildColonyMission;
import net.sf.freecol.server.ai.mission.CashInTreasureTrainMission;
import net.sf.freecol.server.ai.mission.DefendSettlementMission;
import net.sf.freecol.server.ai.mission.IdleAtSettlementMission;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.ai.mission.MissionaryMission;
import net.sf.freecol.server.ai.mission.PioneeringMission;
import net.sf.freecol.server.ai.mission.PrivateerMission;
import net.sf.freecol.server.ai.mission.ScoutingMission;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;
import net.sf.freecol.server.ai.mission.UnitWanderHostileMission;
import net.sf.freecol.server.ai.mission.WishRealizationMission;
import net.sf.freecol.server.ai.mission.WorkInsideColonyMission;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Objects of this class contains AI-information for a single European
 * {@link Player} and is used for controlling this player.
 *
 * The method {@link #startWorking} gets called by the
 * {@link AIInGameInputHandler} when it is this player's turn.
 */
public class EuropeanAIPlayer extends AIPlayer {

    private static final Logger logger = Logger.getLogger(EuropeanAIPlayer.class.getName());

    /** Maximum number of turns to travel to a building site. */
    private static final int buildingRange = 5;

    /** Maximum number of turns to travel to a cash in location. */
    private static final int cashInRange = 20;

    /** Maximum number of turns to travel to a missionary target. */
    private static final int missionaryRange = 20;

    /**
     * Maximum number of turns to travel to make progress on
     * pioneering.  This is low-ish because it is usually more
     * efficient to ship the tools where they are needed and either
     * create a new pioneer on site or send a hardy pioneer on
     * horseback.  The AI is probably smart enough to do the former
     * already, and one day the latter.
     */
    private static final int pioneeringRange = 10;

    /** Maximum number of turns to travel to a scouting target. */
    private static final int scoutingRange = 20;

    /**
     * A comparator to sort units by suitability for a BuildColonyMission.
     *
     * Favours unequipped freeColonists, and other unskilled over experts.
     * Also favour units on the map.
     */
    private static final Comparator<AIUnit> builderComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                if (a == null || (unit = a.getUnit()) == null
                    || BuildColonyMission.invalidReason(a) != null)
                    return -1000;
                int base = (!unit.hasDefaultRole()) ? 0
                    : (unit.getSkillLevel() > 0) ? 100
                    : 500 + 100 * unit.getSkillLevel();
                if (unit.hasTile()) base += 50;
                return base;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    /**
     * A comparator to sort units by suitability for a ScoutingMission.
     *
     * Favours existing scouts (especially if on the map), then dismounted
     * experts, then units that can become scouts.
     *
     * We do not check if a unit is near to a colony that can provide horses,
     * as that is likely to be too expensive.  TODO: revise
     */
    private static final Comparator<AIUnit> scoutComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                return (a == null || (unit = a.getUnit()) == null
                    || unit.getLocation() == null
                    || !unit.isColonist()) ? -1000
                : (unit.hasAbility(Ability.SPEAK_WITH_CHIEF))
                ? 900 + ((unit.hasTile()) ? 100 : 0)
                : (unit.hasAbility(Ability.EXPERT_SCOUT)) ? 600
                : (!unit.hasDefaultRole()) ? 100
                : (unit.getSkillLevel() <= 0) ? 200
                : 0;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    /**
     * A comparator to sort units by suitability for a PioneeringMission.
     *
     * Favours existing pioneers (especially if on the map), then experts
     * missing tools, then units that can become pioneers.
     *
     * We do not check if a unit is near to a colony that can provide tools,
     * as that is likely to be too expensive.  TODO: revise
     */
    private static final Comparator<AIUnit> pioneerComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                return (a == null || (unit = a.getUnit()) == null
                    || unit.getLocation() == null
                    || !unit.isColonist()) ? -1000
                : (unit.hasAbility(Ability.IMPROVE_TERRAIN))
                ? 900 + ((unit.hasTile()) ? 100 : 0)
                : (unit.hasAbility(Ability.EXPERT_PIONEER)) ? 600
                : (!unit.hasDefaultRole()) ? 100
                : (unit.getSkillLevel() > 0) ? 200
                : 200 + unit.getSkillLevel() * 50;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    // These should be final, but need the spec.

    /** Cheat chances. */
    private static int liftBoycottCheatPercent;
    private static int equipScoutCheatPercent;
    private static int equipPioneerCheatPercent;
    private static int landUnitCheatPercent;
    private static int offensiveLandUnitCheatPercent;
    private static int offensiveNavalUnitCheatPercent;
    private static int transportNavalUnitCheatPercent;
    /** The pioneer role. */
    private static Role pioneerRole = null;
    /** The scouting role. */
    private static Role scoutRole = null;

    // Caches/internals.  Do not serialize.

    /**
     * Stores temporary information for sessions (trading with another
     * player etc).
     */
    private final java.util.Map<String, Integer> sessionRegister
        = new HashMap<String, Integer>();

    /**
     * A cached map of the current nation summary for all live nations.
     */
    private final java.util.Map<Player, NationSummary> nationMap
        = new HashMap<Player, NationSummary>();

    /**
     * A cached map of Tile to best TileImprovementPlan.
     * Used to choose a tile improvement for a pioneer to work on.
     */
    private final java.util.Map<Tile, TileImprovementPlan> tipMap
        = new HashMap<Tile, TileImprovementPlan>();

    /**
     * A cached map of destination Location to Wishes awaiting transport.
     */
    private final java.util.Map<Location, List<Wish>> transportDemand
        = new HashMap<Location, List<Wish>>();

    /** A cached list of transportables awaiting transport. */
    private final List<TransportableAIObject> transportSupply
        = new ArrayList<TransportableAIObject>();

    /**
     * A mapping of goods type to the goods wishes where a colony has
     * requested that goods type.  Used to retarget goods that have
     * gone astray.
     */
    private final java.util.Map<GoodsType, List<GoodsWish>> goodsWishes
        = new HashMap<GoodsType, List<GoodsWish>>();

    /**
     * A mapping of unit type to the worker wishes for that type.
     * Used to allocate WishRealizationMissions for units.
     */
    private final java.util.Map<UnitType, List<WorkerWish>> workerWishes
        = new HashMap<UnitType, List<WorkerWish>>();

    /**
     * A mapping of contiguity number to number of wagons needed in
     * that landmass.
     */
    private final java.util.Map<Integer, Integer> wagonsNeeded
        = new HashMap<Integer, Integer>();

    /**
     * Current estimate of the number of new
     * <code>BuildColonyMission</code>s to create.
     */
    private int nBuilders = 0;

    /**
     * Current estimate of the number of new
     * <code>PioneeringMission</code>s to create.
     */
    private int nPioneers = 0;

    /**
     * Current estimate of the number of new
     * <code>ScoutingMission</code>s to create.
     */
    private int nScouts = 0;

    /** Count of the number of transports needing a naval unit. */
    private int nNavalCarrier = 0;


    /**
     * Creates a new <code>EuropeanAIPlayer</code>.
     *
     * @param aiMain The main AI-class.
     * @param player The player that should be associated with this
     *            <code>AIPlayer</code>.
     */
    public EuropeanAIPlayer(AIMain aiMain, ServerPlayer player) {
        super(aiMain, player);

        uninitialized = getPlayer() == null;
    }

    /**
     * Creates a new <code>AIPlayer</code>.
     *
     * @param aiMain The main AI-object.
     * @param xr The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered during parsing.
     */
    public EuropeanAIPlayer(AIMain aiMain,
                            FreeColXMLReader xr) throws XMLStreamException {
        super(aiMain, xr);

        uninitialized = getPlayer() == null;
    }


    /**
     * Initialize the static fields that would be final but for
     * needing the specification.
     *
     * @param spec The <code>Specification</code> to initialize from.
     */
    public static synchronized void initializeFromSpecification(Specification spec) {
        if (pioneerRole != null) return;
        pioneerRole = spec.getRoleWithAbility(Ability.IMPROVE_TERRAIN, null);
        scoutRole = spec.getRoleWithAbility(Ability.SPEAK_WITH_CHIEF, null);
        liftBoycottCheatPercent
            = spec.getInteger(GameOptions.LIFT_BOYCOTT_CHEAT);
        equipScoutCheatPercent
            = spec.getInteger(GameOptions.EQUIP_SCOUT_CHEAT);
        equipPioneerCheatPercent
            = spec.getInteger(GameOptions.EQUIP_PIONEER_CHEAT);
        landUnitCheatPercent
            = spec.getInteger(GameOptions.LAND_UNIT_CHEAT);
        offensiveLandUnitCheatPercent
            = spec.getInteger(GameOptions.OFFENSIVE_LAND_UNIT_CHEAT);
        offensiveNavalUnitCheatPercent
            = spec.getInteger(GameOptions.OFFENSIVE_NAVAL_UNIT_CHEAT);
        transportNavalUnitCheatPercent
            = spec.getInteger(GameOptions.TRANSPORT_NAVAL_UNIT_CHEAT);
    }

    /**
     * Update the nation map.
     */
    public void cacheNations() {
        nationMap.clear();
        for (Player p : getGame().getLiveEuropeanPlayers(null)) {
            NationSummary ns = AIMessage.askGetNationSummary(this, p);
            nationMap.put(p, ns);
        }            
    }

    /**
     * Simple initialization of AI missions given that we know the starting
     * conditions.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void initializeMissions(LogBuilder lb) {
        final AIMain aiMain = getAIMain();
        List<AIUnit> aiUnits = getAIUnits();
        lb.add("\n  Initialize ");
        
        // Find all the carriers with potential colony builders on board,
        // give them missions.
        final Map map = getGame().getMap();
        final int maxRange = map.getWidth() + map.getHeight();
        Location target;
        Mission m;
        TransportMission tm;
        for (AIUnit aiCarrier : aiUnits) {
            if (aiCarrier.hasMission()) continue;
            Unit carrier = aiCarrier.getUnit();
            if (!carrier.isNaval()) continue;
            target = null;
            for (Unit u : carrier.getUnitList()) {
                AIUnit aiu = aiMain.getAIUnit(u);
                for (int range = buildingRange; range < maxRange;
                     range += buildingRange) {
                    target = BuildColonyMission.findTarget(aiu, range, false);
                    if (target != null) break;
                }
                if (target == null) {
                    throw new RuntimeException("Initial colony fail!");
                }
                if ((m = getBuildColonyMission(aiu, target)) != null) {
                    lb.add(m, ", ");
                }
            }
            // Initialize the carrier mission after the cargo units
            // have a valid mission so that the transport list and
            // mission target do not break.
            tm = (TransportMission)getTransportMission(aiCarrier);
            if (tm != null) {
                lb.add(tm);
                for (Unit u : carrier.getUnitList()) {
                    AIUnit aiu = getAIMain().getAIUnit(u);
                    if (aiu == null) continue;
                    tm.queueTransportable(aiu, false, lb);
                }
            }
        }

        // Put in some backup missions.
        lb.mark();
        for (AIUnit aiu : aiUnits) {
            if (aiu.hasMission()) continue;
            if ((m = getSimpleMission(aiu)) != null) lb.add(m, ", ");
        }
        if (lb.grew("\n  Backup: ")) lb.shrink(", ");
    }

    /**
     * Cheats for the AI.  Please try to centralize cheats here.
     *
     * TODO: Remove when the AI is good enough.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void cheat(LogBuilder lb) {
        final AIMain aiMain = getAIMain();
        if (!aiMain.getFreeColServer().isSinglePlayer()) return;

        final Player player = getPlayer();
        if (player.getPlayerType() != PlayerType.COLONIAL) return;
        lb.mark();

        final Specification spec = getSpecification();
        final Game game = getGame();
        final Market market = player.getMarket();
        final Europe europe = player.getEurope();
        final Random air = getAIRandom();
        final List<GoodsType> arrears = new ArrayList<GoodsType>();
        if (market != null) {
            for (GoodsType gt : spec.getGoodsTypeList()) {
                if (market.getArrears(gt) > 0) arrears.add(gt);
            }
        }
        final int nCheats = arrears.size() + 6; // 6 cheats + arrears
        int[] randoms = Utils.randomInts(logger, "cheats", air, 100, nCheats);
        int cheatIndex = 0;

        for (GoodsType goodsType : arrears) {
            if (randoms[cheatIndex++] < liftBoycottCheatPercent) {
                market.setArrears(goodsType, 0);
                // Just remove one goods party modifier (we can not
                // currently identify which modifier applies to which
                // goods type, but that is not worth fixing for the
                // benefit of `temporary' cheat code).  If we do not
                // do this, AI colonies accumulate heaps of party
                // modifiers because of the cheat boycott removal.
                findOne: for (Colony c : player.getColonies()) {
                    for (Modifier m : c.getModifiers()) {
                        if (Specification.COLONY_GOODS_PARTY_SOURCE == m.getSource()) {
                            c.removeModifier(m);
                            lb.add("lift-boycott at ", c, ", ");
                            break findOne;
                        }
                    }
                }
            }
        }
    
        if (!europe.isEmpty()
            && scoutsNeeded() > 0
            && randoms[cheatIndex++] < equipScoutCheatPercent) {
            for (Unit u : europe.getUnitList()) {
                if (u.hasDefaultRole()
                    && u.hasAbility(Ability.CAN_BE_EQUIPPED)) {
                    int price = europe.priceGoods(u.getGoodsDifference(scoutRole, 1));
                    if (!u.getOwner().checkGold(price)) {
                        player.modifyGold(price);
                        lb.add("added ", price, " gold to ");
                    }
                    if (getAIUnit(u).equipForRole("model.role.scout")) {
                        lb.add("equip scout ", u, ", ");
                    }
                    break;
                }
            }
        }

        if (!europe.isEmpty()
            && pioneersNeeded() > 0
            && randoms[cheatIndex++] < equipPioneerCheatPercent) {
            for (Unit u : europe.getUnitList()) {
                if (u.hasDefaultRole()
                    && u.hasAbility(Ability.CAN_BE_EQUIPPED)) {
                    int price = europe.priceGoods(u.getGoodsDifference(pioneerRole, 1));
                    if (!u.getOwner().checkGold(price)) {
                        player.modifyGold(price);
                        lb.add("added ", price, " gold to ");
                    }
                    if (getAIUnit(u).equipForRole("model.role.pioneer")) {
                        lb.add("equip pioneer ", u, ", ");
                    }
                    break;
                }
            }
        }

        if (randoms[cheatIndex++] < landUnitCheatPercent) {
            WorkerWish bestWish = null;
            int bestValue = Integer.MIN_VALUE;
            for (UnitType ut : workerWishes.keySet()) {
                List<WorkerWish> wl = workerWishes.get(ut);
                if (wl == null
                    || wl.isEmpty()
                    || ut == null
                    || !ut.isAvailableTo(player)
                    || europe.getUnitPrice(ut) == UNDEFINED) continue;
                WorkerWish ww = wl.get(0);
                if (bestValue < ww.getValue()) {
                    bestValue = ww.getValue();
                    bestWish = ww;
                }
            }

            int cost;
            if (bestWish != null) {
                cost = europe.getUnitPrice(bestWish.getUnitType());
            } else if (player.getImmigration()
                < player.getImmigrationRequired() / 2) {
                cost = player.getRecruitPrice();
            } else {
                cost = INFINITY;
            }
            if (cost != INFINITY) {
                if (cost > 0 && !player.checkGold(cost)) {
                    player.modifyGold(cost);
                    lb.add("added ", cost, " gold to ");
                }
                AIUnit aiu;
                if (bestWish == null) {
                    if ((aiu = recruitAIUnitInEurope(-1)) != null) {
                        // let giveNormalMissions look after the mission
                        lb.add("recruit ", aiu.getUnit(), ", ");
                    }
                } else {
                    if ((aiu = trainAIUnitInEurope(bestWish.getUnitType())) != null) {
                        Mission m = getWishRealizationMission(aiu, bestWish);
                        if (m != null) {
                            lb.add("train for ", m, ", ");
                        } else {
                            lb.add("train ", aiu.getUnit(), ", ");
                        }
                    }
                }
            }
        }

        if (game.getTurn().getAge() >= 2
            && player.isAtWar()
            && randoms[cheatIndex++] < offensiveLandUnitCheatPercent) {
            // Find a target to attack.
            Location target = null;
            // - collect enemies, prefer not to antagonize the strong or
            //   crush the weak
            List<Player> enemies = new ArrayList<Player>();
            List<Player> preferred = new ArrayList<Player>();
            for (Player p : game.getLivePlayers(player)) {
                if (player.atWarWith(p)) {
                    enemies.add(p);
                    double strength = getStrengthRatio(p);
                    if (strength < 3.0/2.0 && strength > 2.0/3.0) {
                        preferred.add(p);
                    }
                }
            }
            if (!preferred.isEmpty()) {
                enemies.clear();
                enemies.addAll(preferred);
            }
            List<Colony> colonies = player.getColonies();
            // Few colonies?  Attack the weakest European port
            if (colonies.size() < 3) {
                List<Colony> targets = new ArrayList<Colony>();
                for (Player p : enemies) {
                    if (p.isEuropean()) targets.addAll(p.getColonies());
                }
                double targetScore = -1;
                for (Colony c : targets) {
                    if (c.isConnectedPort()) {
                        double score = 100000.0 / c.getUnitCount();
                        Building stockade = c.getStockade();
                        score /= (stockade == null) ? 1.0
                            : (stockade.getLevel() + 1.5);
                        if (targetScore < score) {
                            targetScore = score;
                            target = c;
                        }
                    }
                }
            }
            // Otherwise attack something near a weak colony
            if (target == null && !colonies.isEmpty()) {
                List<AIColony> bad = new ArrayList<AIColony>();
                for (AIColony aic : getAIColonies()) {
                    if (aic.isBadlyDefended()) bad.add(aic);
                }
                if (bad.isEmpty()) bad.addAll(getAIColonies());
                AIColony defend = Utils.getRandomMember(logger,
                    "AIColony to defend", bad, air);
                Tile center = defend.getColony().getTile();
                Tile t = game.getMap().searchCircle(center,
                    GoalDeciders.getEnemySettlementGoalDecider(enemies),
                    30);
                if (t != null) target = t.getSettlement();
            }
            if (target != null) {
                List<Unit> mercs = ((ServerPlayer)player)
                    .createUnits(player.getMonarch().getMercenaries(air),
                                 europe);
                for (Unit u : mercs) {
                    AIUnit aiu = getAIUnit(u);
                    if (aiu == null) continue; // Can not happen
                    Mission m = getSeekAndDestroyMission(aiu, target);
                    if (m != null) {
                        lb.add("enlisted ", m, ", ");
                    } else {
                        lb.add("enlisted ", aiu.getUnit(), ", ");
                    }
                }
            }
        }
            
        // Always cheat a new armed ship if the navy is destroyed,
        // otherwise if the navy is below average the chance to cheat
        // is proportional to how badly below average.
        double naval = getNavalStrengthRatio();
        int nNaval = (player.getUnitCount(true) == 0) ? 100
            : (0.0f < naval && naval < 0.5f)
            ? (int)(naval * offensiveNavalUnitCheatPercent)
            : -1;
        List<RandomChoice<UnitType>> rc
            = new ArrayList<RandomChoice<UnitType>>();
        if (randoms[cheatIndex++] < nNaval) {
            rc.clear();
            for (UnitType unitType : spec.getUnitTypeList()) {
                if (unitType.hasAbility(Ability.NAVAL_UNIT)
                    && unitType.isAvailableTo(player)
                    && unitType.hasPrice()
                    && unitType.isOffensive()) {
                    int weight = 100000 / europe.getUnitPrice(unitType);
                    rc.add(new RandomChoice<UnitType>(unitType, weight));
                }
            }
            cheatUnit(rc, "offensive-naval", lb);
        }
        // Only cheat carriers if they have work to do.
        int nCarrier = (nNavalCarrier > 0) ? transportNavalUnitCheatPercent
            : -1;
        if (randoms[cheatIndex++] < nCarrier) {
            rc.clear();
            for (UnitType unitType : spec.getUnitTypeList()) {
                if (unitType.hasAbility(Ability.NAVAL_UNIT)
                    && unitType.isAvailableTo(player)
                    && unitType.hasPrice()
                    && unitType.getSpace() > 0) {
                    int weight = 100000 / europe.getUnitPrice(unitType);
                    rc.add(new RandomChoice<UnitType>(unitType, weight));
                }
            }
            cheatUnit(rc, "transport-naval", lb);
        }

        if (lb.grew("\n  Cheats: ")) lb.shrink(", ");
    }

    /**
     * Cheat-build a unit in Europe.
     *
     * @param rc A list of random choices to choose from.
     * @param what A description of the unit.
     * @param lb A <code>LogBuilder</code> to log to.
     * @return The <code>AIUnit</code> built.
     */
    private AIUnit cheatUnit(List<RandomChoice<UnitType>> rc, String what,
                             LogBuilder lb) {
        UnitType unitToPurchase
            = RandomChoice.getWeightedRandom(logger, "Cheat which unit",
                                             rc, getAIRandom());
        return cheatUnit(unitToPurchase, what, lb);
    }

    /**
     * Cheat-build a unit in Europe.
     *
     * @param unitType The <code>UnitType</code> to build.
     * @param what A description of the unit.
     * @param lb A <code>LogBuilder</code> to log to.
     * @return The <code>AIUnit</code> built.
     */
    private AIUnit cheatUnit(UnitType unitType, String what, LogBuilder lb) {
        final Player player = getPlayer();
        final Europe europe = player.getEurope();
        int cost = europe.getUnitPrice(unitType);
        if (cost > 0 && !player.checkGold(cost)) {
            player.modifyGold(cost);
            lb.add("added ", cost, " gold to build ");
        }
        AIUnit result = trainAIUnitInEurope(unitType);
        lb.add(what, " ", unitType.getSuffix(),
            ((result != null) ? "" : "(failed)"), ", ");
        return result;
    }

    /**
     * Assign transportable units and goods to available carriers.
     *
     * These supply driven assignments supplement the demand driven
     * calls inside TransportMission.
     *
     * @param missions A list of <code>TransportMission</code>s to potentially
     *     assign more transportables to.
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void allocateTransportables(List<TransportMission> missions,
                                        LogBuilder lb) {
        if (missions.isEmpty()) return;
        List<TransportableAIObject> urgent = getUrgentTransportables();
        if (urgent.isEmpty()) return;

        lb.add("\n  Allocate Transport cargo=", urgent.size(),
               " carriers=", missions.size());
        //for (TransportableAIObject t : urgent) lb.add(" ", t);
        //lb.add(" ->");
        //for (Mission m : missions) lb.add(" ", m);

        TransportMission best;
        float bestValue;
        boolean present;
        int i = 0;
        outer: while (i < urgent.size()) {
            if (missions.isEmpty()) break;
            TransportableAIObject t = urgent.get(i);
            best = null;
            bestValue = 0.0f;
            present = false;
            for (TransportMission tm : missions) {
                if (!tm.spaceAvailable(t)) continue;
                Cargo cargo = tm.makeCargo(t, lb);
                if (cargo == null) { // Serious problem with this cargo
                    urgent.remove(i);
                    continue outer;
                }
                int turns = cargo.getTurns();
                float value;
                if (turns == 0) {
                    value = tm.destinationCapacity();
                    if (!present) bestValue = 0.0f; // reset
                    present = true;
                } else {
                    value = (present) ? -1.0f
                        : (float)t.getTransportPriority() / turns;
                }
                if (bestValue < value) {
                    bestValue = value;
                    best = tm;
                }
            }
            if (best != null) {
                lb.add(best.getUnit(), " chosen");
                if (best.queueTransportable(t, false, lb)) {
                    claimTransportable(t);
                    if (best.destinationCapacity() <= 0) {
                        missions.remove(best);
                    }
                } else {
                    missions.remove(best);
                }
            }
            i++;
        }
    }

    /**
     * Brings gifts to nice players with nearby colonies.
     *
     * TODO: European players can also bring gifts! However,
     * this might be folded into a trade mission, since
     * European gifts are just a special case of trading.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void bringGifts(LogBuilder lb) {
        return;
    }

    /**
     * Demands goods from players with nearby colonies.
     *
     * TODO: European players can also demand tribute!
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void demandTribute(LogBuilder lb) {
        return;
    }


    // Tile Improvement handling

    /**
     * Rebuilds a map of locations to TileImprovementPlans.
     *
     * Called by startWorking at the start of every turn.
     * Public for the test suite.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    public void buildTipMap(LogBuilder lb) {
        tipMap.clear();
        for (AIColony aic : getAIColonies()) {
            for (TileImprovementPlan tip : aic.getTileImprovementPlans()) {
                if (tip == null || tip.isComplete()) {
                    aic.removeTileImprovementPlan(tip);
                } else if (tip.getPioneer() != null) {
                    ; // Do nothing, remove when complete
                } else if (!tip.validate()) {
                    aic.removeTileImprovementPlan(tip);
                    tip.dispose();
                } else if (tip.getTarget() == null) {
                    logger.warning("No target for tip: " + tip);
                } else {
                    TileImprovementPlan other = tipMap.get(tip.getTarget());
                    if (other == null || other.getValue() < tip.getValue()) {
                        tipMap.put(tip.getTarget(), tip);
                    }
                }
            }
        }
        if (!tipMap.isEmpty()) {
            lb.add("\n  Improvements:");
            for (Tile t : tipMap.keySet()) {
                TileImprovementPlan tip = tipMap.get(t);
                AIUnit pioneer = tip.getPioneer();
                lb.add(" ", t, "=", tip.getType().getSuffix());
                if (pioneer != null) lb.add("/", pioneer.getUnit());
            }
        }                
    }

    /**
     * Gets the best plan for a tile from the tipMap.
     *
     * @param tile The <code>Tile</code> to lookup.
     * @return The best plan for a tile.
     */
    public TileImprovementPlan getBestPlan(Tile tile) {
        return (tipMap == null) ? null : tipMap.get(tile);
    }

    /**
     * Gets the best plan for a colony from the tipMap.
     *
     * @param colony The <code>Colony</code> to check.
     * @return The tile with the best plan for a colony, or null if none found.
     */
    public Tile getBestPlanTile(Colony colony) {
        TileImprovementPlan best = null;
        int bestValue = Integer.MIN_VALUE;
        for (Tile t : colony.getOwnedTiles()) {
            TileImprovementPlan tip = tipMap.get(t);
            if (tip != null && tip.getValue() > bestValue) {
                bestValue = tip.getValue();
                best = tip;
            }
        }
        return (best == null) ? null : best.getTarget();
    }

    /**
     * Remove a <code>TileImprovementPlan</code> from the relevant colony.
     */
    public void removeTileImprovementPlan(TileImprovementPlan plan) {
        for (AIColony aic : getAIColonies()) {
            if (aic.removeTileImprovementPlan(plan)) break;
        }
    }


    // Transport maps handling

    /**
     * Checks if a transportable needs transport.
     *
     * @param t The <code>TransportableAIObject</code> to check.
     * @return True if no transport is already present or the
     *     transportable is already aboard a carrier, and there is a
     *     well defined source and destination location.
     */
    private boolean requestsTransport(TransportableAIObject t) {
        return t.getTransport() == null
            && t.getTransportDestination() != null
            && t.getTransportSource() != null
            && !(t.getLocation() instanceof Unit);
    }

    /**
     * Checks that the carrier assigned to a transportable is has a
     * transport mission and the transport is queued thereon.
     *
     * @param t The <code>TransportableAIObject</code> to check.
     * @return True if all is well.
     */
    private boolean checkTransport(TransportableAIObject t) {
        AIUnit aiCarrier = t.getTransport();
        if (aiCarrier == null) return false;
        TransportMission tm = aiCarrier.getMission(TransportMission.class);
        if (tm != null && tm.isTransporting(t)) return true;
        t.changeTransport(null);
        return false;
    }

    /**
     * Gets the needed wagons for a tile/contiguity.
     *
     * @param tile The <code>Tile</code> to derive the contiguity from.
     * @return The number of wagons needed.
     */
    public int getNeededWagons(Tile tile) {
        if (tile != null) {
            int contig = tile.getContiguity();
            if (contig > 0) {
                Integer i = wagonsNeeded.get(contig);
                if (i != null) return i.intValue();
            }
        }
        return 0;
    }

    /**
     * Changes the needed wagons map for a specified tile/contiguity.
     * If the change is zero, that is a special flag that a connected
     * port is available, and thus that the map should be initialized
     * for that contiguity.
     *
     * @param tile The <code>Tile</code> to derive the contiguity from.
     * @param amount The change to make.
     */
    private void changeNeedWagon(Tile tile, int amount) {
        if (tile == null) return;
        int contig = tile.getContiguity();
        if (contig > 0) {
            Integer i = wagonsNeeded.get(contig);
            if (i == null) {
                if (amount == 0) wagonsNeeded.put(contig, new Integer(0));
            } else {
                wagonsNeeded.put(contig, new Integer(i.intValue() + amount));
            }
        }
    }

    /**
     * Rebuild the transport maps.
     * Count the number of transports requiring naval/land carriers.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void buildTransportMaps(LogBuilder lb) {
        transportDemand.clear();
        transportSupply.clear();
        wagonsNeeded.clear();
        nNavalCarrier = 0;

        // Prime the wagonsNeeded map with contiguities with a connected port
        for (AIColony aic : getAIColonies()) {
            Colony colony = aic.getColony();
            if (colony.isConnectedPort()) changeNeedWagon(colony.getTile(), 0);
        }

        for (AIUnit aiu : getAIUnits()) {
            if (aiu.hasMission() && !aiu.getMission().isValid()) continue;
            Unit u = aiu.getUnit();
            if (u.isCarrier()) {
                if (u.isNaval()) {
                    nNavalCarrier--;
                } else {
                    changeNeedWagon(u.getTile(), -1);
                }
            } else {
                checkTransport(aiu);
                if (requestsTransport(aiu)) {
                    transportSupply.add(aiu);
                    aiu.incrementTransportPriority();
                    nNavalCarrier++;
                }
            }
        }

        for (AIColony aic : getAIColonies()) {
            for (AIGoods aig : aic.getAIGoods()) {
                checkTransport(aig);
                if (requestsTransport(aig)) {
                    transportSupply.add(aig);
                    aig.incrementTransportPriority();
                    Location src = aig.getTransportSource();
                    Location dst = aig.getTransportDestination();
                    if (!Map.isSameContiguity(src, dst)) {
                        nNavalCarrier++;
                    }
                }
            }
            Colony colony = aic.getColony();
            if (!colony.isConnectedPort()) {
                changeNeedWagon(colony.getTile(), 1);
            }
        }

        for (Wish w : getWishes()) {
            TransportableAIObject t = w.getTransportable();
            if (t != null && t.getTransport() == null
                && t.getTransportDestination() != null) {
                Location loc = upLoc(t.getTransportDestination());
                Utils.appendToMapList(transportDemand, loc, w);
            }
        }

        if (!transportSupply.isEmpty()) {
            lb.add("\n  Transport Supply:");
            for (TransportableAIObject t : transportSupply) {
                lb.add(" ", t.getTransportPriority(), "+", t);
            }
        }
        if (!transportDemand.isEmpty()) {
            lb.add("\n  Transport Demand:");
            for (Location ld : transportDemand.keySet()) {
                lb.add("\n    ", ld, "[");
                for (Wish w : transportDemand.get(ld)) lb.add(" ", w);
                lb.add(" ]");
            }
        }
    }

    /**
     * Gets the most urgent transportables.
     *
     * @return The most urgent 10% of the available transportables.
     */
    public List<TransportableAIObject> getUrgentTransportables() {
        List<TransportableAIObject> urgent
            = new ArrayList<TransportableAIObject>(transportSupply);
        // Do not let the list exceed 10% of all transports
        Collections.sort(urgent);
        int urge = urgent.size();
        urge = Math.max(2, (urge + 5) / 10);
        while (urgent.size() > urge) urgent.remove(urge);
        return urgent;
    }

    /**
     * Allows a TransportMission to signal that it has taken responsibility
     * for a TransportableAIObject.
     *
     * @param t The <code>TransportableAIObject</code> being claimed.
     * @return True if the transportable was claimed from the supply map.
     */
    public boolean claimTransportable(TransportableAIObject t) {
        return transportSupply.remove(t);
    }

    /**
     * Rearrange colonies, collecting workers that now need a
     * WorkInsideColonyMission.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     * @return A collection of <code>AIUnit</code>s that need work.
     */
    private Collection<AIUnit> rearrangeColonies(LogBuilder lb) {
        Set<AIUnit> workers = new HashSet<AIUnit>();
        for (AIColony aic : getAIColonies()) {
            workers.addAll(aic.rearrangeWorkers(lb));
        }
        return workers;
    }


    // Wish handling

    /**
     * Gets a list of the wishes at a given location for a unit type.
     *
     * @param loc The <code>Location</code> to look for wishes at.
     * @param type The <code>UnitType</code> to look for.
     * @return A list of <code>WorkerWish</code>es.
     */
    public List<WorkerWish> getWorkerWishesAt(Location loc, UnitType type) {
        List<Wish> demand = transportDemand.get(upLoc(loc));
        if (demand == null) return Collections.<WorkerWish>emptyList();
        List<WorkerWish> result = new ArrayList<WorkerWish>();
        for (Wish w : demand) {
            if (w instanceof WorkerWish
                && ((WorkerWish)w).getUnitType() == type) {
                result.add((WorkerWish)w);
            }
        }
        return result;
    }

    /**
     * Gets a list of the wishes at a given location for a goods type.
     *
     * @param loc The <code>Location</code> to look for wishes at.
     * @param type The <code>GoodsType</code> to look for.
     * @return A list of <code>GoodsWish</code>es.
     */
    public List<GoodsWish> getGoodsWishesAt(Location loc, GoodsType type) {
        List<Wish> demand = transportDemand.get(upLoc(loc));
        if (demand == null) return Collections.<GoodsWish>emptyList();
        List<GoodsWish> result = new ArrayList<GoodsWish>();
        for (Wish w : demand) {
            if (w instanceof GoodsWish
                && ((GoodsWish)w).getGoodsType() == type) {
                result.add((GoodsWish)w);
            }
        }
        return result;
    }

    /**
     * Gets the best worker wish for a carrier unit.
     *
     * @param aiUnit The carrier <code>AIUnit</code>.
     * @param unitType The <code>UnitType</code> to find a wish for.
     * @return The best worker wish for the unit.
     */
    public WorkerWish getBestWorkerWish(AIUnit aiUnit, UnitType unitType) {
        List<WorkerWish> wishes = workerWishes.get(unitType);
        if (wishes == null) return null;

        final Unit carrier = aiUnit.getUnit();
        WorkerWish nonTransported = null;
        WorkerWish transported = null;
        float bestNonTransportedValue = -1.0f;
        float bestTransportedValue = -1.0f;
        for (WorkerWish w : wishes) {
            int turns;
            try {
                turns = carrier.getTurnsToReach(w.getDestination());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Bogus wish destination for "
                    + aiUnit + ": " + w.getDestination()
                    + " for wish: " + w, e);
                continue;
            }
            if (turns == INFINITY) {
                if (bestTransportedValue < w.getValue()) {
                    bestTransportedValue = w.getValue();
                    transported = w;
                }
            } else {
                if (bestNonTransportedValue < (float)w.getValue() / turns) {
                    bestNonTransportedValue = (float)w.getValue() / turns;
                    nonTransported = w;
                }
            }
        }
        return (nonTransported != null) ? nonTransported
            : (transported != null) ? transported
            : null;
    }

    /**
     * Gets the best goods wish for a carrier unit.
     *
     * @param aiUnit The carrier <code>AIUnit</code>.
     * @param goodsType The <code>GoodsType</code> to wish for.
     * @return The best <code>GoodsWish</code> for the unit.
     */
    public GoodsWish getBestGoodsWish(AIUnit aiUnit, GoodsType goodsType) {
        List<GoodsWish> wishes = goodsWishes.get(goodsType);
        if (wishes == null) return null;

        final Unit carrier = aiUnit.getUnit();
        float bestValue = 0.0f;
        GoodsWish best = null;
        for (GoodsWish w : wishes) {
            int turns = carrier.getTurnsToReach(carrier.getLocation(),
                                                w.getDestination());
            if (turns == INFINITY) continue;
            float value = (float)w.getValue() / turns;
            if (bestValue > value) {
                bestValue = value;
                best = w;
            }
        }
        return best;
    }

    /**
     * Rebuilds the goods and worker wishes maps.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void buildWishMaps(LogBuilder lb) {
        for (UnitType unitType : getSpecification().getUnitTypeList()) {
            List<WorkerWish> wl = workerWishes.get(unitType);
            if (wl == null) {
                workerWishes.put(unitType, new ArrayList<WorkerWish>());
            } else {
                wl.clear();
            }
        }
        for (GoodsType goodsType : getSpecification().getGoodsTypeList()) {
            if (!goodsType.isStorable()) continue;
            List<GoodsWish> gl = goodsWishes.get(goodsType);
            if (gl == null) {
                goodsWishes.put(goodsType, new ArrayList<GoodsWish>());
            } else {
                gl.clear();
            }
        }

        for (Wish w : getWishes()) {
            if (w instanceof WorkerWish) {
                WorkerWish ww = (WorkerWish)w;
                if (ww.getTransportable() == null) {
                    Utils.appendToMapList(workerWishes, ww.getUnitType(), ww);
                }
            } else if (w instanceof GoodsWish) {
                GoodsWish gw = (GoodsWish)w;
                if (gw.getDestination() instanceof Colony) {
                    Utils.appendToMapList(goodsWishes, gw.getGoodsType(), gw);
                }
            }
        }

        if (!workerWishes.isEmpty()) {
            lb.add("\n  Wishes (workers):");
            for (UnitType ut : workerWishes.keySet()) {
                List<WorkerWish> wl = workerWishes.get(ut);
                if (!wl.isEmpty()) {
                    lb.add("\n    ", ut.getSuffix(), ":");
                    for (WorkerWish ww : wl) {
                        lb.add(" ", ww.getDestination(),
                               "(", ww.getValue(), ")");
                    }
                }
            }
        }
        if (!goodsWishes.isEmpty()) {
            lb.add("\n  Wishes (goods):");
            for (GoodsType gt : goodsWishes.keySet()) {
                List<GoodsWish> gl = goodsWishes.get(gt);
                if (!gl.isEmpty()) {
                    lb.add("\n    ", gt.getSuffix(), ":");
                    for (GoodsWish gw : gl) {
                        lb.add(" ", gw.getDestination(),
                               "(", gw.getValue(), ")");
                    }
                }
            }
        }
    }

    /**
     * Notify that a wish has been completed.  Called from AIColony.
     *
     * @param w The <code>Wish</code> to complete.
     */
    public void completeWish(Wish w) {
        if (w instanceof WorkerWish) {
            WorkerWish ww = (WorkerWish)w;
            List<WorkerWish> wl = workerWishes.get(ww.getUnitType());
            if (wl != null) wl.remove(ww);
        } else if (w instanceof GoodsWish) {
            GoodsWish gw = (GoodsWish)w;
            List<GoodsWish> gl = goodsWishes.get(gw.getGoodsType());
            if (gl != null) gl.remove(gw);
        } else {
            throw new IllegalStateException("Bogus wish: " + w);
        }
    }

    /**
     * Consume a WorkerWish, yielding a WishRealizationMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param ww The <code>WorkerWish</code> to consume.
     */
    public void consumeWorkerWish(AIUnit aiUnit, WorkerWish ww) {
        final Unit unit = aiUnit.getUnit();
        List<WorkerWish> wwL = workerWishes.get(unit.getType());
        wwL.remove(ww);
        List<Wish> wl = transportDemand.get(ww.getDestination());
        if (wl != null) wl.remove(ww);
        ww.setTransportable(aiUnit);
    }

    /**
     * Consume a GoodsWish.
     *
     * @param aig The <code>AIGoods</code> to use.
     * @param gw The <code>GoodsWish</code> to consume.
     */
    public void consumeGoodsWish(AIGoods aig, GoodsWish gw) {
        final Goods goods = aig.getGoods();
        List<GoodsWish> gwL = goodsWishes.get(goods.getType());
        gwL.remove(gw);
        List<Wish> wl = transportDemand.get(gw.getDestination());
        if (wl != null) wl.remove(gw);
        gw.setTransportable(aig);
    }


    // Useful public routines

    /**
     * Gets the number of units that should build a colony.
     *
     * This is the desired total number, not the actual number which would
     * take into account the number of existing BuildColonyMissions.
     *
     * @return The desired number of colony builders for this player.
     */
    public int buildersNeeded() {
        Player player = getPlayer();
        if (!player.canBuildColonies()) return 0;

        int nColonies = 0, nPorts = 0, nWorkers = 0, nEuropean = 0;
        for (Settlement settlement : player.getSettlements()) {
            nColonies++;
            if (settlement.isConnectedPort()) nPorts++;
            for (Unit u : settlement.getUnitList()) {
                if (u.isPerson()) nWorkers++;
            }
            for (Unit u : settlement.getTile().getUnitList()) {
                if (u.isPerson()) nWorkers++;
            }
        }
        Europe europe = player.getEurope();
        if (europe != null) {
            for (Unit u : europe.getUnitList()) {
                if (u.isPerson()) nEuropean++;
            }
        }
            
        // If would be good to have at least two colonies, and at least
        // one port.  After that, determine the ratio of workers to colonies
        // (which should be the average colony size), and if that is above
        // a threshold, send out another colonist.
        // The threshold probably should be configurable.  2 is too
        // low IMHO as it makes a lot of brittle colonies, 3 is too
        // high at least initially as it makes it hard for the initial
        // colonies to become substantial.  For now, arbitrarily choose e.
        return (nColonies == 0 || nPorts == 0) ? 2
            : ((nPorts <= 1) && (nWorkers + nEuropean) >= 3) ? 1
            : ((double)(nWorkers + nEuropean) / nColonies > Math.E) ? 1
            : 0;
    }

    /**
     * How many pioneers should we have?
     *
     * This is the desired total number, not the actual number which would
     * take into account the number of existing PioneeringMissions.
     *
     * @return The desired number of pioneers for this player.
     */
    public int pioneersNeeded() {
        return (tipMap.size() + 1) / 2;
    }

    /**
     * How many scouts should we have?
     *
     * This is the desired total number, not the actual number which would
     * take into account the number of existing ScoutingMissions.
     *
     * Current scheme for European AIs is to use up to three scouts in
     * the early part of the game, then one.
     *
     * @return The desired number of scouts for this player.
     */
    public int scoutsNeeded() {
        return (getGame().getTurn().getAge() <= 1) ? 3 : 1;
    }

    /**
     * Asks the server to recruit a unit in Europe on behalf of the AIPlayer.
     *
     * TODO: Move this to a specialized Handler class (AIEurope?)
     * TODO: Give protected access?
     *
     * @param index The index of the unit to recruit in the recruitables list,
     *     (if not a valid index, recruit a random unit).
     * @return The new AIUnit created by this action or null on failure.
     */
    public AIUnit recruitAIUnitInEurope(int index) {
        AIUnit aiUnit = null;
        Europe europe = getPlayer().getEurope();
        if (europe == null) return null;
        int n = europe.getUnitCount();
        final String selectAbility = Ability.SELECT_RECRUIT;
        int slot = (index >= 0 && index < Europe.RECRUIT_COUNT
            && getPlayer().hasAbility(selectAbility)) ? (index + 1) : 0;
        if (AIMessage.askEmigrate(this, slot)
            && europe.getUnitCount() == n+1) {
            aiUnit = getAIUnit(europe.getUnitList().get(n));
            if (aiUnit != null) addAIUnit(aiUnit);
        }
        return aiUnit;
    }

    /**
     * Helper function for server communication - Ask the server
     * to train a unit in Europe on behalf of the AIGetPlayer().
     *
     * TODO: Move this to a specialized Handler class (AIEurope?)
     * TODO: Give protected access?
     *
     * @return the new AIUnit created by this action. May be null.
     */
    public AIUnit trainAIUnitInEurope(UnitType unitType) {
        if (unitType==null) {
            throw new IllegalArgumentException("Invalid UnitType.");
        }

        AIUnit aiUnit = null;
        Europe europe = getPlayer().getEurope();
        if (europe == null) return null;
        int n = europe.getUnitCount();

        if (AIMessage.askTrainUnitInEurope(this, unitType)
            && europe.getUnitCount() == n+1) {
            aiUnit = getAIUnit(europe.getUnitList().get(n));
            if (aiUnit != null) addAIUnit(aiUnit);
        }
        return aiUnit;
    }

    /**
     * Gets the wishes for all this player's colonies, sorted by the
     * {@link Wish#getValue value}.
     *
     * @return A list of wishes.
     */
    public List<Wish> getWishes() {
        List<Wish> wishes = new ArrayList<Wish>();
        for (AIColony aic : getAIColonies()) {
            wishes.addAll(aic.getWishes());
        }
        Collections.sort(wishes);
        return wishes;
    }


    // Diplomacy support

    /**
     * Determines the stances towards each player.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    private void determineStances(LogBuilder lb) {
        final Player player = getPlayer();
        lb.mark();

        for (Player p : getGame().getLivePlayers(player)) {
            Stance newStance = determineStance(p);
            if (newStance != player.getStance(p)) {
                if (newStance == Stance.WAR && peaceHolds(p)) {
                    ; // Peace treaty holds for now
                } else {
                    getAIMain().getFreeColServer().getInGameController()
                        .changeStance(player, newStance, p, true);
                    lb.add(" ", p.getNation().getSuffix(),
                           "->", newStance, ", ");
                }
            }
        }
        if (lb.grew("\n  Stance changes:")) lb.shrink(", ");
    }

    /**
     * See if a recent peace treaty still has force.
     *
     * @param p The <code>Player</code> to check for a peace treaty with.
     * @return True if peace gets another chance.
     */
    private boolean peaceHolds(Player p) {
        final Player player = getPlayer();
        final Turn turn = getGame().getTurn();
        final double peaceProb = getSpecification()
            .getInteger(GameOptions.PEACE_PROBABILITY) / 100.0;

        int peaceTurn = -1;
        for (HistoryEvent h : player.getHistory()) {
            if (p.getId().equals(h.getPlayerId())
                && h.getTurn().getNumber() > peaceTurn) {
                switch (h.getEventType()) {
                case MAKE_PEACE: case FORM_ALLIANCE:
                    peaceTurn = h.getTurn().getNumber();
                    break;
                case DECLARE_WAR:
                    peaceTurn = -1;
                    break;
                default:
                    break;
                }
            }
        }
        if (peaceTurn < 0) return false;

        int n = turn.getNumber() - peaceTurn;
        float prob = (float)Math.pow(peaceProb, n);
        // Apply Franklin's modifier
        prob = p.applyModifiers(prob, turn, Modifier.PEACE_TREATY);
        return prob > 0.0f
            && (Utils.randomInt(logger, "Peace holds?",  getAIRandom(), 100)
                < (int)(100.0f * prob));
    }

    /**
     * Get the land force strength ratio of this player with respect
     * to another.
     *
     * @param other The other <code>Player</code>.
     * @return The strength ratio (strength/sum(strengths)).
     */
    protected double getStrengthRatio(Player other) {
        NationSummary ns = nationMap.get(other);
        int strength = getPlayer().calculateStrength(false);
        return (ns == null) ? -1.0 : 
            (double)strength / (strength + ns.getMilitaryStrength());
    }

    /**
     * Is this player lagging in naval strength?  Calculate the ratio
     * of its naval strength to the average strength of other European
     * colonial powers.
     *
     * @return The naval strength ratio, or negative if there are no other
     *     European colonial nations.
     */
    protected double getNavalStrengthRatio() {
        final Player player = getPlayer();
        double navalAverage = 0.0;
        double navalStrength = 0.0;
        int nPlayers = 0;
        for (Entry<Player, NationSummary> e : nationMap.entrySet()) {
            Player p = e.getKey();
            if (p.isREF()) continue;
            if (p == player) {
                navalStrength = e.getValue().getNavalStrength();
            } else {
                int ns = e.getValue().getNavalStrength();
                if (ns >= 0) navalAverage += ns;
                nPlayers++;
            }
        }
        if (nPlayers <= 0 || navalStrength < 0) return -1.0;
        navalAverage /= nPlayers;
        return (navalAverage == 0.0) ? -1.0 : navalStrength / navalAverage;
    }

    /**
     * Evaluate a colony for trade purposes.
     *
     * @param colony The <code>Colony</code> to evaluate.
     * @return The score.
     */
    private int evaluateColony(Colony colony) {
        if (colony == null) return 0;
        final Player player = getPlayer();
        int result = 0;

        if (player.owns(colony)) {
            int v;
            for (WorkLocation wl : colony.getAvailableWorkLocations()) {
                for (Unit u : wl.getUnitList()) {
                    result += evaluateUnit(u);
                }
                if (wl instanceof Building) {
                    for (AbstractGoods ag : ((Building)wl).getType()
                             .getRequiredGoods()) {
                        result += evaluateGoods(ag);
                    }
                } else if (wl instanceof ColonyTile) {
                    for (AbstractGoods ag : ((ColonyTile)wl)
                             .getProductionInfo().getProduction()) {
                        result += evaluateGoods(ag);
                    }
                }
            }
            for (Unit u : colony.getTile().getUnitList()) {
                result += evaluateUnit(u);
            }
            for (Goods g : colony.getCompactGoods()) {
                result += evaluateGoods(g);
            }
        } else {
            // Much guesswork
            result += colony.getDisplayUnitCount() * 1000;
            result += 500; // Some useful goods?
            for (Tile t : colony.getTile().getSurroundingTiles(1)) {
                if (t.getOwningSettlement() == colony) result += 200;
            }
            Building stockade = colony.getStockade();
            if (stockade != null) {
                result *= stockade.getLevel();
            }
        }
        return result;
    }

    /**
     * Evaluate a unit for trade purposes.
     *
     * @param unit The <code>Unit</code> to evaluate.
     * @return The score.
     */
    private int evaluateUnit(Unit unit) {
        final Europe europe = getPlayer().getEurope();

        return (europe == null) ? 0
            : europe.getUnitPrice(unit.getType());
    }

    /**
     * Evaluate goods for trade purposes.
     *
     * @param ag The <code>AbstractGoods</code> to evaluate.
     * @return The score.
     */
    private int evaluateGoods(AbstractGoods ag) {
        final Market market = getPlayer().getMarket();

        return (market == null) ? 0
            : market.getSalePrice(ag.getType(), ag.getAmount());
    }

    /**
     * Reject a trade agreement, except if a Franklin-derived stance
     * is supplied.
     *
     * @param stance A stance <code>TradeItem</code>.
     * @param agreement The <code>DiplomaticTrade</code> to reset.
     * @return The <code>TradeStatus</code> for the agreement.
     */
    private TradeStatus rejectAgreement(TradeItem stance,
                                        DiplomaticTrade agreement) {
        if (stance == null) return TradeStatus.REJECT_TRADE;
        
        agreement.clear();
        agreement.add(stance);
        return TradeStatus.PROPOSE_TRADE;
    }


    // Mission handling

    /**
     * Ensures all units have a mission.
     *
     * @param lb A <code>LogBuilder</code> to log to.
     */
    protected void giveNormalMissions(LogBuilder lb) {
        final AIMain aiMain = getAIMain();
        final Player player = getPlayer();
        final int turnNumber = getGame().getTurn().getNumber();
        java.util.Map<Unit, String> reasons = new HashMap<Unit, String>();
        BuildColonyMission bcm = null;
        Mission m;

        nBuilders = buildersNeeded();
        nPioneers = pioneersNeeded();
        nScouts = scoutsNeeded();

        List<AIUnit> aiUnits = getAIUnits();
        List<AIUnit> navalUnits = new ArrayList<AIUnit>();
        List<AIUnit> done = new ArrayList<AIUnit>();
        List<TransportMission> transportMissions
            = new ArrayList<TransportMission>();

        // For all units, check if it is a candidate for a new
        // mission.  If it is not a candidate remove it from the
        // aiUnits list (reporting why not).  Adjust the
        // Build/Pioneer/Scout counts according to the existing valid
        // missions.  Accumulate potentially usable transport missions.
        lb.mark();
        for (AIUnit aiUnit : aiUnits) {
            final Unit unit = aiUnit.getUnit();
            final Colony colony = unit.getColony();
            m = aiUnit.getMission();

            if (unit.isUninitialized() || unit.isDisposed()) {
                reasons.put(unit, "Invalid");

            } else if (unit.isDamaged()) { // Damaged units must wait
                if (!(m instanceof IdleAtSettlementMission)) {
                    if ((m = getIdleAtSettlementMission(aiUnit)) != null) {
                        lb.add(m, ", ");
                    }
                }
                reasons.put(unit, "Damaged");
                    
            } else if (unit.getState() == UnitState.IN_COLONY
                && colony.getUnitCount() <= 1) {
                // The unit has its hand full keeping the colony alive.
                if (!(m instanceof WorkInsideColonyMission)
                    && (m = getWorkInsideColonyMission(aiUnit,
                            aiMain.getAIColony(colony))) != null) {
                    logger.warning(aiUnit + " should WorkInsideColony at "
                        + colony.getName());
                    lb.add(m, ", ");
                }
                reasons.put(unit, "Vital");

            } else if (unit.isInMission()) {
                reasons.put(unit, "Mission");

            } else if (m != null && m.isValid() && !m.isOneTime()) {
                if (m instanceof BuildColonyMission) {
                    bcm = (BuildColonyMission)m;
                    nBuilders--;
                } else if (m instanceof PioneeringMission) {
                    nPioneers--;
                } else if (m instanceof ScoutingMission) {
                    nScouts--;
                } else if (m instanceof TransportMission) {
                    TransportMission tm = (TransportMission)m;
                    if (tm.destinationCapacity() > 0) {
                        transportMissions.add(tm);
                    }
                }
                reasons.put(unit, "Valid");

            } else if (unit.isNaval()) {
                navalUnits.add(aiUnit);

            } else if (unit.isAtSea()) { // Wait for it to emerge
                reasons.put(unit, "At-Sea");

            } else { // Unit needs a mission
                continue;
            }
            done.add(aiUnit);
        }
        aiUnits.removeAll(done);
        done.clear();

        // First try to satisfy the demand for missions with a defined
        // quota.  Builders first to keep weak players in the game,
        // scouts next as they are profitable.  Pile onto any
        // exisiting building mission if there are no colonies.
        if (player.getNumberOfSettlements() <= 0 && bcm != null) {
            Collections.sort(aiUnits, builderComparator);
            for (AIUnit aiUnit : aiUnits) {
                if ((m = getBuildColonyMission(aiUnit,
                            bcm.getTarget())) == null) continue;
                lb.add(m, ", ");
                done.add(aiUnit);
                if (requestsTransport(aiUnit)) {
                    transportSupply.add(aiUnit);
                }
                reasons.put(aiUnit.getUnit(), "0Builder");
            }
            aiUnits.removeAll(done);
            done.clear();
        }
        if (nBuilders > 0) {
            Collections.sort(aiUnits, builderComparator);
            for (AIUnit aiUnit : aiUnits) {
                if ((m = getBuildColonyMission(aiUnit,
                                               null)) == null) continue;
                lb.add(m, ", ");
                done.add(aiUnit);
                if (requestsTransport(aiUnit)) {
                    transportSupply.add(aiUnit);
                }
                reasons.put(aiUnit.getUnit(), "Builder" + nBuilders);
                if (--nBuilders <= 0) break;
            }
            aiUnits.removeAll(done);
            done.clear();
        }
        if (nScouts > 0) {
            Collections.sort(aiUnits, scoutComparator);
            for (AIUnit aiUnit : aiUnits) {
                final Unit unit = aiUnit.getUnit();
                if ((m = getScoutingMission(aiUnit)) == null) continue;
                lb.add(m, ", ");
                done.add(aiUnit);
                if (requestsTransport(aiUnit)) {
                    transportSupply.add(aiUnit);
                }
                reasons.put(unit, "Scout" + nScouts);
                if (--nScouts <= 0) break;
            }
            aiUnits.removeAll(done);
            done.clear();
        }
        if (nPioneers > 0) {
            Collections.sort(aiUnits, pioneerComparator);
            for (AIUnit aiUnit : aiUnits) {
                final Unit unit = aiUnit.getUnit();
                if ((m = getPioneeringMission(aiUnit)) == null) continue;
                lb.add(m, ", ");
                done.add(aiUnit);
                if (requestsTransport(aiUnit)) {
                    transportSupply.add(aiUnit);
                }
                reasons.put(unit, "Pioneer" + nPioneers);
                if (--nPioneers <= 0) break;
            }
            aiUnits.removeAll(done);
            done.clear();
        }

        // Give the remaining land units a valid mission.
        for (AIUnit aiUnit : aiUnits) {
            final Unit unit = aiUnit.getUnit();
            if ((m = getSimpleMission(aiUnit)) == null) continue;
            lb.add(m, ", ");
            reasons.put(unit, "New-Land");
            done.add(aiUnit);
            if (requestsTransport(aiUnit)) {
                transportSupply.add(aiUnit);
            }
        }
        aiUnits.removeAll(done);
        done.clear();

        // Process the free naval units, possibly adding to the usable
        // transport missions.
        for (AIUnit aiUnit : navalUnits) {
            final Unit unit = aiUnit.getUnit();
            if ((m = getSimpleMission(aiUnit)) == null) continue;
            lb.add(m, ", ");
            reasons.put(unit, "New-Naval");
            done.add(aiUnit);
            if (m instanceof TransportMission) {
                TransportMission tm = (TransportMission)m;
                if (tm.destinationCapacity() > 0) {
                    transportMissions.add(tm);
                }
                // A new transport mission might have retargeted
                // its passengers into new valid missions.
                for (Unit u : aiUnit.getUnit().getUnitList()) {
                    AIUnit aiu = getAIUnit(u);
                    Mission um = aiu.getMission();
                    if (um != null && um.isValid()
                        && aiUnits.contains(aiu)) {
                        aiUnits.remove(aiu);
                        reasons.put(aiu.getUnit(), "New");
                    }
                }
            }
        }
        navalUnits.removeAll(done);
        done.clear();

        // Give remaining units the fallback mission.
        aiUnits.addAll(navalUnits);
        List<Colony> ports = null;
        int nPorts = player.getNumberOfPorts();
        for (AIUnit aiUnit : aiUnits) {
            final Unit unit = aiUnit.getUnit();
            m = aiUnit.getMission();
            if (m != null && m.isValid() && !m.isOneTime()) {
                // Might have picked up a reason in allocateTransportables
                continue;
            }

            if (unit.isInEurope() && unit.isPerson() && nPorts > 0) {
                // Choose a port to add to
                if (ports == null) ports = player.getPorts();
                Colony c = ports.remove(0);
                if ((m = getWorkInsideColonyMission(aiUnit,
                            aiMain.getAIColony(c))) != null) lb.add(m, ", ");
                reasons.put(unit, "To-work");
                ports.add(c);

            } else if (m instanceof IdleAtSettlementMission) {
                reasons.put(unit, "Idle"); // already idle
            } else {
                if ((m = getIdleAtSettlementMission(aiUnit)) != null)
                    lb.add(m, ", ");
                reasons.put(unit, "Idle");
            }
        }
        if (lb.grew("\n  Mission changes: ")) lb.shrink(", ");

        // Now see if transport can be found
        allocateTransportables(transportMissions, lb);

        // Log
        if (!aiUnits.isEmpty()) {
            lb.add("\n  Free Land Units:");
            for (AIUnit aiu : aiUnits) {
                lb.add(" ", aiu.getUnit());
            }
        }
        if (!navalUnits.isEmpty()) {
            lb.add("\n  Free Naval Units:");
            for (AIUnit aiu : navalUnits) {
                lb.add(" ", aiu.getUnit());
            }
        }
        lb.add("\n  Missions(colonies=", player.getNumberOfSettlements(),
            " builders=", nBuilders,
            " pioneers=", nPioneers,
            " scouts=", nScouts,
            " naval-carriers=", nNavalCarrier,
            ")");
        logMissions(reasons, lb);
    }

    /**
     * Choose a mission for an AIUnit.
     *
     * @param aiUnit The <code>AIUnit</code> to choose for.
     * @return A suitable <code>Mission</code>, or null if none found.
     */
    public Mission getSimpleMission(AIUnit aiUnit) {
        final Unit unit = aiUnit.getUnit();
        Mission m, ret;
        final Mission old = ((m = aiUnit.getMission()) != null && m.isValid())
            ? m : null;

        if (unit.isNaval()) {
            ret = //(old instanceof PrivateerMission) ? old
                ((m = getPrivateerMission(aiUnit, null)) != null) ? m
                // (old instanceof TransportMission) ? old
                : ((m = getTransportMission(aiUnit)) != null) ? m
                // (old instanceof UnitSeekAndDestroyMission) ? old
                : ((m = getSeekAndDestroyMission(aiUnit, 8)) != null) ? m
                // (old instanceof UnitWanderHostileMission) ? old
                : getWanderHostileMission(aiUnit);

        } else if (unit.isCarrier()) {
            ret = getTransportMission(aiUnit);

        } else {
            // CashIn missions are obvious
            ret = ((m = getCashInTreasureTrainMission(aiUnit)) != null) ? m

                // Working in colony is obvious
                : (unit.isInColony()
                    && (m = getWorkInsideColonyMission(aiUnit, null)) != null) ? m

                // Try to maintain defence
                : ((m = getDefendSettlementMission(aiUnit, false)) != null) ? m

                // Favour wish realization for expert units
                : (unit.isColonist() && unit.getSkillLevel() > 0
                    && (m = getWishRealizationMission(aiUnit, null)) != null) ? m

                // Try nearby offence
                : ((m = getSeekAndDestroyMission(aiUnit, 8)) != null) ? m

                // Missionary missions are only available to some units
                : ((m = getMissionaryMission(aiUnit)) != null) ? m

                // Try to satisfy any remaining wishes, such as population
                : ((m = getWishRealizationMission(aiUnit, null)) != null) ? m

                // Another try to defend, with relaxed cost decider
                : ((m = getDefendSettlementMission(aiUnit, true)) != null) ? m

                // Another try to attack, at longer range
                : ((m = getSeekAndDestroyMission(aiUnit, 16)) != null) ? m

                // Leftover offensive units should go out looking for trouble
                : ((m = getWanderHostileMission(aiUnit)) != null) ? m

                : null;
        }
        return ret;
    }

    // Mission creation convenience routines.
    // Aggregated here for uniformity.  Might have been more logical
    // to disperse them to the individual classes.

    /**
     * Gets a new BuildColonyMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param target An optional target <code>Location</code>.
     * @return A new mission, or null if impossible.
     */
    public Mission getBuildColonyMission(AIUnit aiUnit, Location target) {
        String reason = BuildColonyMission.invalidReason(aiUnit);
        if (reason != null) return null;
        final Unit unit = aiUnit.getUnit();
        if (target == null) {
            target = BuildColonyMission.findTarget(aiUnit, buildingRange,
                                                   unit.isInEurope());
        }
        return (target == null) ? null
            : new BuildColonyMission(getAIMain(), aiUnit, target);
    }

    /**
     * Gets a new CashInTreasureTrainMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getCashInTreasureTrainMission(AIUnit aiUnit) {
        String reason = CashInTreasureTrainMission.invalidReason(aiUnit);
        if (reason != null) return null;
        final Unit unit = aiUnit.getUnit();
        Location loc = CashInTreasureTrainMission.findTarget(aiUnit,
            cashInRange, unit.isInEurope());
        return (loc == null) ? null
            : new CashInTreasureTrainMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new DefendSettlementMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param relaxed Use a relaxed cost decider to choose the target.
     * @return A new mission, or null if impossible.
     */
    public Mission getDefendSettlementMission(AIUnit aiUnit, boolean relaxed) {
        if (DefendSettlementMission.invalidReason(aiUnit) != null) return null;
        final Unit unit = aiUnit.getUnit();
        final Location loc = unit.getLocation();
        double worstValue = 1000000.0;
        Colony worstColony = null;
        for (AIColony aic : getAIColonies()) {
            Colony colony = aic.getColony();
            if (aic.isBadlyDefended()) {
                if (unit.isAtLocation(colony.getTile())) {
                    worstColony = colony;
                    break;
                }
                double value = colony.getDefenceRatio() * 100.0
                    / unit.getTurnsToReach(loc, colony.getTile(),
                        unit.getCarrier(),
                        ((relaxed) ? CostDeciders.numberOfTiles() : null));
                if (worstValue > value) {
                    worstValue = value;
                    worstColony = colony;
                }
            }
        }
        return (worstColony == null) ? null
            : getDefendSettlementMission(aiUnit, worstColony);
    }

    /**
     * Gets a new MissionaryMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getMissionaryMission(AIUnit aiUnit) {
        if (MissionaryMission.prepare(aiUnit) != null) return null;
        Location loc = MissionaryMission.findTarget(aiUnit, missionaryRange,
                                                    true);
        return (loc == null) ? null
            : new MissionaryMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new PioneeringMission for a unit.
     * TODO: pioneers to make roads between colonies
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getPioneeringMission(AIUnit aiUnit) {
        if (PioneeringMission.prepare(aiUnit) != null) return null;
        Location loc = PioneeringMission.findTarget(aiUnit, pioneeringRange,
                                                    true);
        return (loc == null) ? null
            : new PioneeringMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new PrivateerMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param target An optional target <code>Location</code>.
     * @return A new mission, or null if impossible.
     */
    public Mission getPrivateerMission(AIUnit aiUnit, Location target) {
        if (PrivateerMission.invalidReason(aiUnit) != null) return null;
        if (target == null) {
            target = PrivateerMission.findTarget(aiUnit, 8, true);
        }
        return (target == null) ? null
            : new PrivateerMission(getAIMain(), aiUnit, target);
    }

    /**
     * Gets a new ScoutingMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getScoutingMission(AIUnit aiUnit) {
        if (ScoutingMission.prepare(aiUnit) != null) return null;
        Location loc = ScoutingMission.findTarget(aiUnit, scoutingRange, true);
        return (loc == null) ? null
            : new ScoutingMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new TransportMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getTransportMission(AIUnit aiUnit) {
        if (TransportMission.invalidReason(aiUnit) != null) return null;
        return new TransportMission(getAIMain(), aiUnit);
    }

    /**
     * Gets a new WishRealizationMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param wish An optional <code>WorkerWish</code> to realize.
     * @return A new mission, or null if impossible.
     */
    public Mission getWishRealizationMission(AIUnit aiUnit, WorkerWish wish) {
        if (WishRealizationMission.invalidReason(aiUnit) != null) return null;
        final Unit unit = aiUnit.getUnit();
        if (wish == null) {
            wish = getBestWorkerWish(aiUnit, unit.getType());
        }
        if (wish == null) return null;
        consumeWorkerWish(aiUnit, wish);
        return new WishRealizationMission(getAIMain(), aiUnit, wish);
    }

    /**
     * Gets a WorkInsideColonyMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param aiColony An optional <code>AIColony</code> to work at.
     * @return A new mission, or null if impossible.
     */
    public Mission getWorkInsideColonyMission(AIUnit aiUnit,
                                              AIColony aiColony) {
        if (WorkInsideColonyMission.invalidReason(aiUnit) != null) return null;
        if (aiColony == null) {
            aiColony = getAIColony(aiUnit.getUnit().getColony());
        }
        return (aiColony == null) ? null
            : new WorkInsideColonyMission(getAIMain(), aiUnit, aiColony);
    }


    // AIPlayer interface

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAIColony(AIColony aic) {
        final Colony colony = aic.getColony();
        
        Set<TileImprovementPlan> tips = new HashSet<TileImprovementPlan>();
        for (Tile t : colony.getOwnedTiles()) {
            TileImprovementPlan tip = tipMap.remove(t);
            if (tip != null) tips.add(tip);
        }

        for (AIGoods aig : aic.getAIGoods()) {
            if (Map.isSameLocation(aig.getLocation(), colony)) {
                aig.changeTransport(null);
                aig.dispose();
            }
        }

        transportSupply.remove(colony);
        transportDemand.remove(colony);

        Set<Wish> wishes = new HashSet<Wish>(aic.getWishes());
        for (AIUnit aiu : getAIUnits()) {
            PioneeringMission pm = aiu.getMission(PioneeringMission.class);
            if (pm != null) {
                if (tips.contains(pm.getTileImprovementPlan())) {
                    logger.info(pm + " collapses with loss of " + colony);
                    aiu.changeMission(null);
                }
                continue;
            }
            WishRealizationMission
                wm = aiu.getMission(WishRealizationMission.class);
            if (wm != null) {
                if (wishes.contains(wm.getWish())) {
                    logger.info(wm + " collapses with loss of " + colony);
                    aiu.changeMission(null);
                }
                continue;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void startWorking() {
        final Player player = getPlayer();
        final Turn turn = getGame().getTurn();
        final Specification spec = getSpecification();
        initializeFromSpecification(spec);

        // This is happening, very rarely.  Hopefully now fixed by
        // synchronizing access to AIMain.aiObjects.
        if (getAIMain().getAIPlayer(player) != this) {
            throw new RuntimeException("EuropeanAIPlayer integrity fail");
        }
        sessionRegister.clear();
        clearAIUnits();
        cacheNations();

        // Note call to getAIUnits().  This triggers
        // AIPlayer.createAIUnits which we want to do early, certainly
        // before cheat() or other operations that might make new units
        // happen.
        LogBuilder lb = new LogBuilder(1024);
        int colonyCount = getAIColonies().size();
        lb.add(player.getNation().getSuffix(),
               " in ", turn, "/", turn.getNumber(),
               " units=", getAIUnits().size(),
               " colonies=", colonyCount,
               " declare=", (player.checkDeclareIndependence() == null),
               " v-land-REF=", player.getRebelStrengthRatio(false),
               " v-naval-REF=", player.getRebelStrengthRatio(true));
        if (turn.isFirstTurn()) initializeMissions(lb);
        determineStances(lb);

        if (colonyCount > 0) {
            lb.add("\n  Badly defended:"); // TODO: prioritize defence
            for (AIColony aic : getAIColonies()) {
                if (aic.isBadlyDefended()) lb.add(" ", aic.getColony());
            }

            lb.add("\n  Update colonies:");
            for (AIColony aic : getAIColonies()) aic.update(lb);

            buildTipMap(lb);
            buildWishMaps(lb);
        }
        cheat(lb);
        buildTransportMaps(lb);

        // Note order of operations below.  We allow rearrange et al to run
        // even when there are no movable units left because this expedites
        // mission assignment.
        List<AIUnit> aiUnits = getAIUnits();
        for (int i = 0; i < 3; i++) {
            rearrangeColonies(lb);
            giveNormalMissions(lb);
            bringGifts(lb);
            demandTribute(lb);
            if (aiUnits.isEmpty()) break;
            aiUnits = doMissions(aiUnits, lb);
        }
        lb.log(logger, Level.FINEST);

        clearAIUnits();
        tipMap.clear();
        transportDemand.clear();
        transportSupply.clear();
        wagonsNeeded.clear();
        goodsWishes.clear();
        workerWishes.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<AIUnit> doMissions(List<AIUnit> aiUnits, LogBuilder lb) {
        lb.add("\n  Do missions:");
        List<AIUnit> result = new ArrayList<AIUnit>();
        for (AIUnit aiu : aiUnits) {
            final Unit unit = aiu.getUnit();
            if (unit == null || unit.isDisposed()) continue;
            final Mission old = aiu.getMission();
            if (old == null) {
                // WishRealizationMission cancelled by transport delivery?
                result.add(aiu);
                continue;
            }
            final Location oldTarget = (old == null) ? null : old.getTarget();
            final AIUnit aiCarrier = aiu.getTransport();
            final TransportMission tm = (aiCarrier == null) ? null
                : aiCarrier.getMission(TransportMission.class);
            lb.add("\n  ", unit, " ");
            try {
                aiu.doMission(lb);
            } catch (Exception e) {
                lb.add(", EXCEPTION: ", e.getMessage());
                logger.log(Level.WARNING, "doMissions failed for: " + aiu, e);
            }
            if (unit.isDisposed()) {
                if (tm != null) {
                    lb.add(", drop transport ", aiCarrier.getUnit());
                    tm.removeTransportable(aiu);
                }
                lb.add(", DIED.");
                continue;
            }
            Mission m = aiu.getMission();
            Location newTarget = (m == null) ? null : m.getTarget();
            if (tm != null
                && oldTarget != null
                && !Map.isSameLocation(newTarget, oldTarget)) {
                if (unit.isOnCarrier()
                    || unit.shouldTakeTransportTo(newTarget)) {
                    tm.requeueTransportable(aiu, lb);
                } else {
                    tm.removeTransportable(aiu);
                    lb.add(", drop transport ", aiCarrier.getUnit());
                }
            }
            if (unit.getMovesLeft() > 0 && (!unit.isOnCarrier()
                    || unit.getCarrier().getMovesLeft() > 0)) {
                lb.add("+");
                result.add(aiu);
            } else {
                lb.add(".");
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public int adjustMission(AIUnit aiUnit, PathNode path, Class type,
                             int value) {
        if (value > 0) {
            if (type == DefendSettlementMission.class) {
                // Reduce value in proportion to the number of defenders.
                Location loc = DefendSettlementMission.extractTarget(aiUnit, path);
                if (!(loc instanceof Colony)) {
                    throw new IllegalStateException("European players defend colonies: " + loc);
                }
                Colony colony = (Colony)loc;
                int defenders = getSettlementDefenders(colony);
                value -= 25 * defenders;
                // Reduce value according to the stockade level.
                if (colony.hasStockade()) {
                    if (defenders > colony.getStockade().getLevel() + 1) {
                        value -= 100 * colony.getStockade().getLevel();
                    } else {
                        value -= 20 * colony.getStockade().getLevel();
                    }
                }
            }
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean indianDemand(Unit unit, Colony colony,
                                GoodsType goods, int gold) {
        // TODO: make a better choice, check whether the colony is
        // well defended
        return !"conquest".equals(getAIAdvantage());
    }

    /**
     * {@inheritDoc}
     */
    public TradeStatus acceptDiplomaticTrade(DiplomaticTrade agreement) {
        final Player player = getPlayer();
        final Player other = agreement.getOtherPlayer(player);
        final Market market = player.getMarket();
        final boolean franklin
            = other.hasAbility(Ability.ALWAYS_OFFERED_PEACE);
        final java.util.Map<TradeItem, Integer> scores
            = new HashMap<TradeItem, Integer>();
        TradeItem peace = null;
        TradeItem cash = null;
        LogBuilder lb = new LogBuilder(64);
        lb.add("Evaluate trade offer from ", other.getName(), ":");
        TradeStatus result = null;

        int unacceptable = 0;
        for (TradeItem item : agreement.getTradeItems()) {
            int value;
            if (item instanceof GoldTradeItem) {
                cash = item;
                int gold = ((GoldTradeItem)item).getGold();
                if (item.getSource() == player) {
                    value = -gold;
                } else {
                    value = gold;
                }

            } else if (item instanceof ColonyTradeItem) {
                if (item.getSource() == player) {
                    if (player.getNumberOfSettlements() < 5) {
                        value = Integer.MIN_VALUE;
                    } else {
                        value = -evaluateColony(item.getColony(getGame()));
                    }
                } else {
                    value = evaluateColony(item.getColony(getGame()));
                }

            } else if (item instanceof GoodsTradeItem) {
                Goods goods = ((GoodsTradeItem)item).getGoods();
                if (item.getSource() == player) {
                    value = -market.getBidPrice(goods.getType(),
                                                goods.getAmount());
                } else {
                    value = market.getSalePrice(goods.getType(),
                                                goods.getAmount());
                }

            } else if (item instanceof InciteTradeItem) {
                // TODO, rebalance this
                Player victim = item.getVictim();
                switch (player.getStance(victim)) {
                case ALLIANCE:
                    value = -1;
                    break;
                case WAR: // Not invalid, other player may not know our stance
                    value = 0;
                    break;
                default:
                    double ratio = getStrengthRatio(victim);
                    if (item.getSource() == player) {
                        value = -(int)Math.round(30 * ratio);
                    } else {
                        value = (int)Math.round(30 * ratio);
                    }
                    break;
                }

            } else if (item instanceof StanceTradeItem) {
                double ratio = getStrengthRatio(other);
                // TODO: evaluate whether we want this stance change
                Stance stance = ((StanceTradeItem)item).getStance();
                switch (stance) {
                case WAR:
                    if (ratio < 0.33) {
                        value = Integer.MIN_VALUE;
                    } else if (ratio < 0.5) {
                        value = -(int)Math.round(100 * ratio);
                    } else {
                        value = (int)Math.round(100 * ratio);
                    }
                    break;
                case PEACE:
                    if (agreement.getContext() == DiplomaticTrade.TradeContext.CONTACT) {
                        peace = item;
                        value = 0;
                        break;
                    }
                    // Fall through
                case CEASE_FIRE: case ALLIANCE:
                    if (franklin) {
                        peace = item;
                        value = 0;
                    } else if (ratio > 0.66) {
                        value = Integer.MIN_VALUE;
                    } else if (ratio > 0.5) {
                        value = -(int)Math.round(100 * ratio);
                    } else if (ratio > 0.33) {
                        value = (int)Math.round(100 * ratio);
                    } else {
                        value = 1000;
                    }                       
                    break;
                case UNCONTACTED:
                default:
                    value = Integer.MIN_VALUE;
                    break;
                }

            } else if (item instanceof UnitTradeItem) {
                if (item.getSource() == player) {
                    if (player.getUnits().size() < 10) {
                        value = Integer.MIN_VALUE;
                    } else {
                        value = -evaluateUnit(item.getUnit());
                    }
                } else {
                    value = evaluateUnit(item.getUnit());
                }

            } else {
                throw new RuntimeException("Bogus item: " + item);
            }

            if (value == Integer.MIN_VALUE) unacceptable++;
            scores.put(item, new Integer(value));
            lb.add(" ", Messages.message(item.getDescription()), " = ", value);
        }

        // If too many items are unacceptable, reject
        double ratio = (double)unacceptable
            / (unacceptable + agreement.getTradeItems().size());
        if (ratio > 0.5 - 0.5 * agreement.getVersion()) {
            result = rejectAgreement(peace, agreement);
            lb.add(", Too many (", unacceptable, ") unacceptable");
        }

        int value = 0;
        if (result == null) {
            // Dump the unacceptable offers, sum the rest
            for (Entry<TradeItem, Integer> entry : scores.entrySet()) {
                if (entry.getValue() == Integer.MIN_VALUE) {
                    agreement.remove(entry.getKey());
                } else {
                    value += entry.getValue();
                }
            }
            // If the result is non-negative,
            // accept/propose-without-unacceptable
            if (value >= 0) {
                result = (agreement.getContext() == DiplomaticTrade.TradeContext.CONTACT
                    && agreement.getVersion() == 0) ? TradeStatus.PROPOSE_TRADE
                    : (unacceptable == 0) ? TradeStatus.ACCEPT_TRADE
                    : (agreement.isEmpty()) ? TradeStatus.REJECT_TRADE
                    : TradeStatus.PROPOSE_TRADE;
                lb.add(", Value = ", value);
            }
        }

        if (result == null) {
            // Give up?
            if (Utils.randomInt(logger, "Enough diplomacy?", getAIRandom(),
                                1 + agreement.getVersion()) > 5) {
                result = rejectAgreement(peace, agreement);
                lb.add(", Ran out of patience at ", agreement.getVersion());
            }
        }

        if (result == null) {
            // Dump the negative offers until the sum is positive.
            // Return a proposal with items we like/can accept, or reject
            // if none are left.
            List<TradeItem> items
                = new ArrayList<TradeItem>(agreement.getTradeItems());
            Collections.sort(items, new Comparator<TradeItem>() {
                    public int compare(TradeItem t1, TradeItem t2) {
                        return scores.get(t1) - scores.get(t2);
                    }
                });
            while (!items.isEmpty()) {
                TradeItem item = items.remove(0);
                value += scores.get(item);
                agreement.remove(item);
                if (value > 0) break;
            }
            if (value > 0 && !items.isEmpty()) {
                result = TradeStatus.PROPOSE_TRADE;
                lb.add(", Pruned until acceptable at ", value);
            } else {
                result = rejectAgreement(peace, agreement);
                lb.add(", Agreement unsalvageable");
            }
        }

        lb.add(" => ", result);
        lb.log(logger, Level.INFO);
        return result;
    }


    /**
     * {@inheritDoc}
     */
    public void registerSellGoods(Goods goods) {
        String goldKey = "tradeGold#" + goods.getType().getId()
            + "#" + goods.getAmount() + "#" + goods.getLocation().getId();
        sessionRegister.put(goldKey, null);
    }

    /**
     * {@inheritDoc}
     */
    public int buyProposition(Unit unit, Settlement settlement, Goods goods,
                              int gold) {
        logger.finest("Entering method buyProposition");
        final Specification spec = getSpecification();
        Player buyer = unit.getOwner();
        IndianSettlement is = (IndianSettlement)settlement;
        String goldKey = "tradeGold#" + goods.getType().getId()
            + "#" + goods.getAmount() + "#" + settlement.getId();
        String hagglingKey = "tradeHaggling#" + unit.getId();
        Integer registered = sessionRegister.get(goldKey);
        if (registered == null) {
            int price = is.getPriceToSell(goods)
                + getPlayer().getTension(buyer).getValue();
            if (is.hasMissionary(buyer)
                && spec.getBoolean(GameOptions.ENHANCED_MISSIONARIES)) {
                Unit u = is.getMissionary();
                price = (int)FeatureContainer.applyModifiers(price,
                    getGame().getTurn(), u.getMissionaryTradeModifiers(false));
            }
            sessionRegister.put(goldKey, new Integer(price));
            return price;
        } else {
            int price = registered.intValue();
            if (price < 0 || price == gold) {
                return price;
            } else if (gold < (price * 9) / 10) {
                logger.warning("Cheating attempt: sending a offer too low");
                sessionRegister.put(goldKey, new Integer(-1));
                return NetworkConstants.NO_TRADE;
            } else {
                int haggling = 1;
                if (sessionRegister.containsKey(hagglingKey)) {
                    haggling = sessionRegister.get(hagglingKey).intValue();
                }
                if (Utils.randomInt(logger, "Buy gold", getAIRandom(),
                        3 + haggling) <= 3) {
                    sessionRegister.put(goldKey, new Integer(gold));
                    sessionRegister.put(hagglingKey, new Integer(haggling + 1));
                    return gold;
                } else {
                    sessionRegister.put(goldKey, new Integer(-1));
                    return NetworkConstants.NO_TRADE_HAGGLE;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int sellProposition(Unit unit, Settlement settlement, Goods goods, 
                               int gold) {
        logger.finest("Entering method sellProposition");
        Colony colony = (Colony) settlement;
        Player otherPlayer = unit.getOwner();
        // don't pay for more than fits in the warehouse
        int amount = colony.getWarehouseCapacity() - colony.getGoodsCount(goods.getType());
        amount = Math.min(amount, goods.getAmount());
        // get a good price
        Tension.Level tensionLevel = getPlayer().getTension(otherPlayer).getLevel();
        int percentage = (9 - tensionLevel.ordinal()) * 10;
        // what we could get for the goods in Europe (minus taxes)
        int netProfits = ((100 - getPlayer().getTax())
                          * getPlayer().getMarket().getSalePrice(goods.getType(), amount)) / 100;
        int price = (netProfits * percentage) / 100;
        return price;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptTax(int tax) {
        boolean ret = true;
        LogBuilder lb = new LogBuilder(64);
        lb.add("Tax demand to ", getPlayer().getId(), " of ", tax, "%: ");
        Goods toBeDestroyed = getPlayer().getMostValuableGoods();
        GoodsType goodsType = (toBeDestroyed == null) ? null
            : toBeDestroyed.getType();

        // Is this cheat to look at what the crown will destroy?
        if (toBeDestroyed == null) {
            ret = false;
            lb.add("refused: no-goods-under-threat");
        } else if (goodsType.isFoodType()) {
            ret = false;
            lb.add("refused: food-type");
        } else if (goodsType.isBreedable()) {
            // Refuse if we already have this type under production in
            // multiple places.
            int n = 0;
            for (Settlement s : getPlayer().getSettlements()) {
                if (s.getGoodsCount(goodsType) > 0) n++;
            }
            ret = n < 2;
            if (ret) {
                lb.add("accepted: breedable-type-", goodsType.getSuffix(), 
                       "-missing");
            } else {
                lb.add("refused: breedable-type-", goodsType.getSuffix(),
                       "-present-in-", n, "-settlements");
            }
        } else if (goodsType.isMilitaryGoods()
            || goodsType.isTradeGoods()
            || goodsType.isBuildingMaterial()) {
            // By age 3 we should be able to produce enough ourselves.
            // TODO: check whether we have an armory, at least
            int age = getGame().getTurn().getAge();
            ret = age < 3;
            lb.add(((ret) ? "accepted" : "rejected"),
                   ": special-goods-in-age-", age);
        } else {
            int averageIncome = 0;
            int numberOfGoods = 0;
            // TODO: consider the amount of goods produced. If we
            // depend on shipping huge amounts of cheap goods, we
            // don't want these goods to be boycotted.
            List<GoodsType> goodsTypes = getSpecification().getGoodsTypeList();
            for (GoodsType type : goodsTypes) {
                if (type.isStorable()) {
                    averageIncome += getPlayer().getIncomeAfterTaxes(type);
                    numberOfGoods++;
                }
            }
            averageIncome /= numberOfGoods;
            int income = getPlayer().getIncomeAfterTaxes(toBeDestroyed.getType());
            ret = income < averageIncome;
            lb.add(((ret) ? "accepted" : "rejected"),
                   ": standard-goods-with-income-", income,
                    ((ret) ? "-less-than-" : "-greater-than-"), averageIncome);
        }
        lb.log(logger, Level.INFO);
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptMercenaries() {
        return getPlayer().isAtWar() || "conquest".equals(getAIAdvantage());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FoundingFather selectFoundingFather(List<FoundingFather> ffs) {
        int age = getGame().getTurn().getAge();
        FoundingFather bestFather = null;
        int bestWeight = Integer.MIN_VALUE;
        for (FoundingFather father : ffs) {
            if (father == null) continue;

            // For the moment, arbitrarily: always choose the one
            // offering custom houses.  Allowing the AI to build CH
            // early alleviates the complexity problem of handling all
            // TransportMissions correctly somewhat.
            if (father.hasAbility(Ability.BUILD_CUSTOM_HOUSE)) {
                bestFather = father;
                break;
            }

            int weight = father.getWeight(age);
            if (weight > bestWeight) {
                bestWeight = weight;
                bestFather = father;
            }
        }
        return bestFather;
    }


    // Serialization

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }
}
