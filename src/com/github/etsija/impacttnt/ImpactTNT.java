package com.github.etsija.impacttnt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ImpactTNT extends JavaPlugin {
	
	public static ImpactTNT impactTNT;
	Logger _log = Logger.getLogger("Minecraft");
	private PluginManager pm;
	private final ImpactTNTEvents tntEvents = new ImpactTNTEvents();
	public static List<CannonDispenser> cannons = new ArrayList<CannonDispenser>();
	
	public static boolean expOnImpact;		// Does the TNT explode when hitting something other than air?
	public static int     fuseTicks;		// How many ticks until it explodes anyway
	public static int     expRadius;		// Explosion radius
	public static int     safetyRadius;		// It won't explode until when it has reached this distance from the player who threw it
	public static boolean reqNamedTNT;		// Does the plugin require special TNT renamed as "ImpactTNT" to work?
	public static boolean dispenserCannon;	// Do the dispensers work as cannons, shooting out ImpactTNT?
	public static int     maxSector;		// Maximum sector for the dispenser cannons
	public static int     maxAngle;			// Maximum angle for the dispenser cannons
	
	@Override
	public void onEnable() {
		pm = this.getServer().getPluginManager();
		pm.registerEvents(tntEvents, this);
		processConfigFile();
		loadCannons();
		_log.info("[ImpactTNT] enabled.");
	}

	@Override
	public void onDisable() {
		saveCannons();
		_log.info("[ImpactTNT] disabled.");
	}

	// Handle reading & updating the config.yml
	public void processConfigFile() {

		final Map<String, Object> defParams = new HashMap<String, Object>();
		FileConfiguration config = this.getConfig();
		config.options().copyDefaults(true);
		
		// This is the default configuration
		defParams.put("general.explodeonimpact", true);
		defParams.put("general.fuseticks", 200);
		defParams.put("general.explosionradius", 5);
		defParams.put("general.safetyradius", 10);
		defParams.put("general.reqnamedtnt", false);
		defParams.put("general.dispensercannon", true);
		defParams.put("general.maxsector", 45);
		defParams.put("general.maxangle", 60);
		
		// If config does not include a default parameter, add it
		for (final Entry<String, Object> e : defParams.entrySet())
			if (!config.contains(e.getKey()))
				config.set(e.getKey(), e.getValue());
		
		// Save default values to config.yml in datadirectory
		this.saveConfig();
		
		// Read plugin config parameters from config.yml
		expOnImpact     = getConfig().getBoolean("general.explodeonimpact");
		fuseTicks       = getConfig().getInt("general.fuseticks");
		expRadius       = getConfig().getInt("general.explosionradius");
		safetyRadius    = getConfig().getInt("general.safetyradius");
		reqNamedTNT     = getConfig().getBoolean("general.reqnamedtnt");
		dispenserCannon = getConfig().getBoolean("general.dispensercannon");
		maxSector       = getConfig().getInt("general.maxsector");
		maxAngle        = getConfig().getInt("general.maxangle");
	}		

	// Save the list of dispenser cannons into a datafile for persistence
	public void saveCannons() {
		File saveFile = new File(this.getDataFolder(), "cannons.dat");
		
		if (cannons.size() == 0) return;
		
		// In case the savefile doesn't exist, create it
		if (!saveFile.exists()) {
			try {
				saveFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// Save the data
		try {
			FileOutputStream   fStream = new FileOutputStream(saveFile);
			ObjectOutputStream oStream = new ObjectOutputStream(fStream);
			
			oStream.writeInt(cannons.size());
			for (CannonDispenser c : cannons) {
				//oStream.writeObject(c.getLocation());
				oStream.writeObject(c.getLocation().getWorld().getName());
				oStream.writeDouble(c.getLocation().getX());
				oStream.writeDouble(c.getLocation().getY());
				oStream.writeDouble(c.getLocation().getZ());
				oStream.writeInt(c.getDirection());
				oStream.writeInt(c.getAngle());
			}
			oStream.close();	
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	// Load the dispenser cannons from a datafile
	public void loadCannons() {
		File saveFile = new File(this.getDataFolder(), "cannons.dat");
		if (saveFile.exists()) {
			FileInputStream   fStream = null;
			ObjectInputStream oStream = null;
			
			// Read the data in
			try {
				fStream = new FileInputStream(saveFile);
				oStream = new ObjectInputStream(fStream);
				int count = oStream.readInt();
				for (int i = 0; i < count; i++) {
					World w = Bukkit.getServer().getWorld(oStream.readObject().toString());
					Double x = oStream.readDouble();
					Double y = oStream.readDouble();
					Double z = oStream.readDouble();
					int direction = oStream.readInt();
					int angle     = oStream.readInt();
					Location loc = new Location(w, x, y, z);
					CannonDispenser cannon = new CannonDispenser(loc, direction, angle);
					cannons.add(cannon);
				}
				_log.info("[ImpactTNT] Read in successfully " + count + " dispenser cannons.");
			} catch (FileNotFoundException e) {
				_log.info("Could not locate cannons.dat");
				e.printStackTrace();
			} catch (IOException e) {
				_log.info("IO error when trying to read cannons.dat");
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				_log.info("Could not read cannons.dat, class not found");
				e.printStackTrace();
			} finally {
				try {
					fStream.close();
				} catch (IOException e) {
					_log.info("Error reading cannons.dat, could not close the stream");
					e.printStackTrace();
				}
			}
		}
	}
	
	// Check that all dispenser cannon params are within the new limits
	public void checkLimits() {
		for (CannonDispenser c : cannons) {
			if (c.getDirection() < -maxSector) {
				c.setDirection(-maxSector);
			} else if (c.getDirection() > maxSector) {
				c.setDirection(maxSector);
			}
			if (c.getAngle() > maxAngle) {
				c.setAngle(maxAngle);
			}
		}
	}
	
	// Plugin commands
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		Player player;
		if (sender instanceof Player) {
			player = (Player) sender;
		} else {
			return false;
		}
		
		// Hold TNT in hand -> /impact -> the TNT in your hand has been changed to "ImpactTNT"
		if (cmd.getName().equalsIgnoreCase("impact")) {
			if (reqNamedTNT) {
				if (args.length == 0) { 
					ItemStack i = player.getItemInHand();
					if (i.getType() == Material.TNT) {
						i = renameTNT(player, i);
					} else {
						return false;
					}
					player.sendMessage(ChatColor.GREEN + "One stack of TNT renamed");
					return true;
				
				// Switch all TNT in your inventory between TNT<->ImpactTNT
				} else if (args[0].equalsIgnoreCase("all")) {
					PlayerInventory pi = player.getInventory();
					for (ItemStack i : pi) {
						if (i != null) {
							i = renameTNT(player, i);
						}
					}
					player.sendMessage(ChatColor.GREEN + "All TNT in your inventory renamed");
					return true;
				
				// Save the cannon data
				} else if (args[0].equalsIgnoreCase("save")) {
					saveCannons();
					player.sendMessage(ChatColor.GREEN + "" + cannons.size() + " dispenser cannons saved to cannons.dat");
					return true;
				
				// Change the maximum sector into (-maxSector...maxSector)
				} else if (args[0].equalsIgnoreCase("maxsector")) {
					if (args.length == 2) {
						int i = Integer.parseInt(args[1]);
						if ((i > 0) && (i <= 90)) {
							maxSector = i;
						}
						this.getConfig().set("general.maxsector", maxSector);
						this.saveConfig();
						checkLimits();
					}
					player.sendMessage(ChatColor.GREEN + "Valid cannon sector is now ("
									 + String.format("%+03d", -maxSector) + ","
								     + String.format("%+03d",  maxSector) + ")");
					return true;
				
				// Change the maximum angle to (0...maxAngle)
				} else if (args[0].equalsIgnoreCase("maxangle")) {
					if (args.length == 2) {
						int i = Integer.parseInt(args[1]);
						if ((i > 0) && (i < 90)) {
							maxAngle = i;
						}
						this.getConfig().set("general.maxangle", maxAngle);
						this.saveConfig();
						checkLimits();
					}
					player.sendMessage(ChatColor.GREEN + "Valid cannon angle is now (0,"
									 + String.format("%+03d",  maxAngle) + ")");
					return true;
				}
			} else {
				player.sendMessage("ImpactTNT plugin not configured to need renamed TNT.");
				player.sendMessage("Set general.reqnamedtnt in config.yml to your needs.");
				return true;
			}
		}
		return false;
	}
	
	// Rename a stack of TNT into "ImpactTNT", or if it is already named as such, delete the name
	private ItemStack renameTNT(Player player, ItemStack i) {
		ItemStack iBack = i;
		ItemMeta im = iBack.getItemMeta();
		if (iBack.getType() == Material.TNT) {
			if (!im.hasDisplayName() ||
				!im.getDisplayName().equalsIgnoreCase("ImpactTNT")) {
				im.setDisplayName("ImpactTNT");
				iBack.setItemMeta(im);
			} else {
				im.setDisplayName(null);
				iBack.setItemMeta(im);
			}
		}
		return iBack;
	}
}