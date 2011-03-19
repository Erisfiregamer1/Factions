package com.bukkit.mcteam.factions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bukkit.mcteam.factions.struct.Relation;
import com.bukkit.mcteam.factions.struct.Role;
import com.bukkit.mcteam.gson.reflect.TypeToken;
import com.bukkit.mcteam.util.DiscUtil;

public class FPlayer {
	public static transient Map<String, FPlayer> instances = new HashMap<String, FPlayer>();
	public static transient File file = new File(Factions.instance.getDataFolder(), "players.json");
	
	public transient String playername;
	public transient FLocation lastStoodAt = new FLocation(); // Where did this player stand the last time we checked?
	
	public int factionId;
	public Role role;
	private String title;
	private double power;
	private long lastPowerUpdateTime;
	private boolean mapAutoUpdating;
	private boolean factionChatting; 
	
	public FPlayer(Player player) {
		this.playername = player.getName();
	}
	
	public FPlayer(String playername) {
		this.playername = playername;
	}
	
	// GSON need this noarg constructor.
	public FPlayer() {
		this.resetFactionData();
		this.power = this.getPowerMax();
		this.lastPowerUpdateTime = System.currentTimeMillis();
		this.mapAutoUpdating = false;
	}
	
	public void resetFactionData() {
		this.factionId = 0; // The default neutral faction
		this.factionChatting = false;
		this.role = Role.NORMAL;
		this.title = "";
	}
	
	public Player getPlayer() {
		return Factions.instance.getServer().getPlayer(playername);
	}
	
	public String getPlayerName() {
		return this.playername;
	}
	
	// -------------------------------------------- //
	// Online / Offline State Checking
	// -------------------------------------------- //
	
	public boolean isOnline() {
		return Factions.instance.getServer().getPlayer(playername) != null;
	}
	
	public boolean isOffline() {
		return ! isOnline();
	}
	
	public boolean isFactionChatting() {
		if (this.factionId == 0) {
			return false;
		}
		return factionChatting;
	}

	public void setFactionChatting(boolean factionChatting) {
		this.factionChatting = factionChatting;
	}


	
	public boolean isMapAutoUpdating() {
		return mapAutoUpdating;
	}

	public void setMapAutoUpdating(boolean mapAutoUpdating) {
		this.mapAutoUpdating = mapAutoUpdating;
	}
	
	//----------------------------------------------//
	// Title, Name, Faction Tag and Chat
	//----------------------------------------------//
	
	// Base:
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		save();
	}
	
	public String getName() {
		return this.playername;
	}
	
	public String getTag() {
		if ( ! this.hasFaction()) {
			return "";
		}
		return this.getFaction().getTag();
	}
	
	// Base concatenations:
	
	public String getNameAndSomething(String something) {
		String ret = this.role.getPrefix();
		if (something.length() > 0) {
			ret += something+" ";
		}
		ret += this.getName();
		return ret;
	}
	
	public String getNameAndTitle() {
		return this.getNameAndSomething(this.getTitle());
	}
	
	public String getNameAndTag() {
		return this.getNameAndSomething(this.getTag());
	}
	
	// Colored concatenations:
	// These are used in information messages
	
	public String getNameAndTitle(Faction faction) {
		return this.getRelationColor(faction)+this.getNameAndTitle();
	}
	public String getNameAndTitle(FPlayer follower) {
		return this.getRelationColor(follower)+this.getNameAndTitle();
	}
	
	public String getNameAndTag(Faction faction) {
		return this.getRelationColor(faction)+this.getNameAndTag();
	}
	public String getNameAndTag(FPlayer follower) {
		return this.getRelationColor(follower)+this.getNameAndTag();
	}
	
	public String getNameAndRelevant(Faction faction) {
		// Which relation?
		Relation rel = this.getRelation(faction);
		
		// For member we show title
		if (rel == Relation.MEMBER) {
			return rel.getColor() + this.getNameAndTitle();
		}
		
		// For non members we show tag
		return rel.getColor() + this.getNameAndTag();
	}
	public String getNameAndRelevant(FPlayer follower) {
		return getNameAndRelevant(follower.getFaction());
	}
	
	// Chat Tag: 
	// These are injected into the format of global chat messages.
	
	public String getChatTag() {
		if ( ! this.hasFaction()) {
			return "";
		}
		
		return String.format(Conf.chatTagFormat, this.role.getPrefix()+this.getTag());
	}
	
	// Colored Chat Tag
	public String getChatTag(Faction faction) {
		if ( ! this.hasFaction()) {
			return "";
		}
		
		return this.getRelation(faction).getColor()+getChatTag();
	}
	public String getChatTag(FPlayer follower) {
		if ( ! this.hasFaction()) {
			return "";
		}
		
		return this.getRelation(follower).getColor()+getChatTag();
	}
	
	// -------------------------------
	// Relation and relation colors
	// -------------------------------
	
	public Relation getRelation(Faction faction) {
		return faction.getRelation(this);
	}
	
	public Relation getRelation(FPlayer follower) {
		return this.getFaction().getRelation(follower);
	}
	
	public ChatColor getRelationColor(Faction faction) {
		return faction.getRelationColor(this);
	}
	
	public ChatColor getRelationColor(FPlayer follower) {
		return this.getRelation(follower).getColor();
	}
	
	
	//----------------------------------------------//
	// Health
	//----------------------------------------------//
	public void heal(int amnt) {
		Player player = this.getPlayer();
		if (player == null) {
			return;
		}
		player.setHealth(player.getHealth() + amnt);
	}
	
	
	//----------------------------------------------//
	// Power
	//----------------------------------------------//
	public double getPower() {
		this.updatePower();
		return this.power;
	}
	
	protected void alterPower(double delta) {
		this.power += delta;
		if (this.power > this.getPowerMax()) {
			this.power = this.getPowerMax();
		} else if (this.power < this.getPowerMin()) {
			this.power = this.getPowerMin();
		}
		//Log.debug("Power of "+this.getName()+" is now: "+this.power);
	}
	
	public double getPowerMax() {
		return Conf.powerPlayerMax;
	}
	
	public double getPowerMin() {
		return Conf.powerPlayerMin;
	}
	
	public int getPowerRounded() {
		return (int) Math.round(this.getPower());
	}
	
	public int getPowerMaxRounded() {
		return (int) Math.round(this.getPowerMax());
	}
	
	public int getPowerMinRounded() {
		return (int) Math.round(this.getPowerMin());
	}
	
	protected void updatePower() {
		long now = System.currentTimeMillis();
		long millisPassed = now - this.lastPowerUpdateTime;
		this.lastPowerUpdateTime = now;
		
		int millisPerMinute = 60*1000;
		this.alterPower(millisPassed * Conf.powerPerMinute / millisPerMinute);
		//this.save(); // This would save to often. So we save this on player quit instead.
	}
	
	public void onDeath() {
		this.updatePower();
		this.alterPower(-Conf.powerPerDeath);
	}
	
	//----------------------------------------------//
	// Territory
	//----------------------------------------------//
	public boolean isInOwnTerritory() {
		return Board.getIdAt(new FLocation(this)) == this.factionId;
	}
	
	public boolean isInOthersTerritory() {
		int idHere = Board.getIdAt(new FLocation(this));
		return idHere != 0 && idHere != this.factionId;
	}
	
	public void sendFactionHereMessage() {
		Faction factionHere = Board.getFactionAt(new FLocation(this));
		String msg = Conf.colorSystem+" ~ "+factionHere.getTag(this);
		if (factionHere.id != 0) {
			msg += " - "+factionHere.getDescription();
		}
		this.sendMessage(msg);
	}
	
	//----------------------------------------------//
	// Faction management
	//----------------------------------------------//
	public Faction getFaction() {
		return Faction.get(factionId);
	}
	
	public boolean hasFaction() {
		return factionId != 0;
	}
	
	public ArrayList<String> invite(FPlayer follower) {
		ArrayList<String> errors = new ArrayList<String>();
		
		//Log.debug("this.role: "+this.role);
		//Log.debug("this.role.value: "+this.role.value);
		//Log.debug("FactionRole.MODERATOR.value: "+FactionRole.MODERATOR.value);
		
		if (this.role.value < Role.MODERATOR.value) {
			errors.add(Conf.colorSystem+"You must be a moderator to invite.");
		}
		
		if(errors.size() > 0) {
			return errors;
		}
		
		return this.getFaction().invite(follower);
	}
	
	public ArrayList<String> deinvite(FPlayer follower) {
		ArrayList<String> errors = new ArrayList<String>();
		
		if (this.role.value < Role.MODERATOR.value) {
			errors.add(Conf.colorSystem+"You must be a moderator to deinvite.");
		}
		
		if(errors.size() > 0) {
			return errors;
		}
		
		return this.getFaction().deinvite(follower);
	}
	
	public ArrayList<String> kick(FPlayer follower) {
		ArrayList<String> errors = new ArrayList<String>();
		
		if ( ! follower.getFaction().equals(this.getFaction())) {
			errors.add(follower.getNameAndRelevant(this)+Conf.colorSystem+" is not a member of "+Conf.colorMember+this.getFaction().getTag());
		} else if (follower.equals(this)) {
			errors.add(Conf.colorSystem+"You cannot kick yourself.");
			errors.add(Conf.colorSystem+"You might want to "+Conf.colorCommand+Conf.aliasBase.get(0)+" "+Conf.aliasLeave.get(0));
		} else if (follower.role.value >= this.role.value) { // TODO add more informative messages.
			errors.add(Conf.colorSystem+"Your rank is too low to kick this player.");
		}
		
		if(errors.size() > 0) {
			return errors;
		}
		
		return follower.getFaction().kick(follower);
	}
	
	// -------------------------------------------- //
	// Get and search
	// You can only get a "skin" for online players.
	// The same object is always returned for the same player.
	// This means you can use the == operator. No .equals method necessary.
	// -------------------------------------------- //
	public static FPlayer get(String playername) {
		if (instances.containsKey(playername)) {
			return instances.get(playername);
		}
		
		FPlayer vplayer = new FPlayer(playername);
		instances.put(playername, vplayer);
		return vplayer;
	}
	
	// You should use this one to be sure you do not spell the player name wrong.
	public static FPlayer get(Player player) {
		return get(player.getName());
	}
	
	public static Set<FPlayer> getAllOnline() {
		Set<FPlayer> fplayers = new HashSet<FPlayer>();
		for (Player player : Factions.instance.getServer().getOnlinePlayers()) {
			fplayers.add(FPlayer.get(player));
		}
		return fplayers;
	}
	
	public static Collection<FPlayer> getAll() {
		return instances.values();
	}
	
	public static FPlayer find(String playername) {
		for (Entry<String, FPlayer> entry : instances.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(playername)) {
				return entry.getValue();
			}
		}
		return null;
	}
	
	// -------------------------------------------- //
	// Messages
	// -------------------------------------------- //
	public void sendMessage(String message) {
		this.getPlayer().sendMessage(Conf.colorSystem + message);
	}
	
	public void sendMessage(List<String> messages) {
		for(String message : messages) {
			this.sendMessage(message);
		}
	}
	
	// -------------------------------------------- //
	// Persistance
	// -------------------------------------------- //
	
	public boolean shouldBeSaved() {
		return this.factionId != 0;
	}
	
	public static boolean save() {
		Factions.log("Saving players to disk");
		
		// We only wan't to save the vplayers with non default values
		Map<String, FPlayer> vplayersToSave = new HashMap<String, FPlayer>();
		for (Entry<String, FPlayer> entry : instances.entrySet()) {
			if (entry.getValue().shouldBeSaved()) {
				vplayersToSave.put(entry.getKey(), entry.getValue());
			}
		}
		
		try {
			DiscUtil.write(file, Factions.gson.toJson(vplayersToSave));
		} catch (IOException e) {
			Factions.log("Failed to save the players to disk.");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean load() {
		if ( ! file.exists()) {
			Factions.log("No players to load from disk. Creating new file.");
			save();
			return true;
		}
		
		try {
			Type type = new TypeToken<Map<String, FPlayer>>(){}.getType();
			instances = Factions.gson.fromJson(DiscUtil.read(file), type);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		fillPlayernames();
			
		return true;
	}
	
	public static void fillPlayernames() {
		for(Entry<String, FPlayer> entry : instances.entrySet()) {
			entry.getValue().playername = entry.getKey();
		}
	}
	
}