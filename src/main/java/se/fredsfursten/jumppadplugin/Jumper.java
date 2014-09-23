package se.fredsfursten.jumppadplugin;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Jumper implements Listener {
	private static Jumper singleton = null;
	private static final String ADD_COMMAND = "/jumppad add <name> <up speed> [<forward speed>]";
	private static final String GOTO_COMMAND = "/jumppad goto <name>";
	private static final String REMOVE_COMMAND = "/jumppad remove <name>";
	private static final String EDIT_COMMAND = "/jumppad edit <up speed> [<forward speed>]";
	private static final String RULES_COMMAND_BEGINNING = "/rules";

	private HashMap<Player, Player> _informedPlayers = null;
	private HashMap<Player, Player> _noJumpPlayers = null;
	private HashMap<Player, JumpPadInfo> _inAirPlayers = null;
	private JavaPlugin _plugin = null;
	private AllJumpPads _allJumpPads = null;

	private Jumper() {
		_allJumpPads = AllJumpPads.get();
	}

	static Jumper get()
	{
		if (singleton == null) {
			singleton = new Jumper();
		}
		return singleton;
	}

	void enable(JavaPlugin plugin){
		_plugin = plugin;

		_informedPlayers = new HashMap<Player, Player>();
		_noJumpPlayers = new HashMap<Player, Player>();
		_inAirPlayers = new HashMap<Player, JumpPadInfo>();

		_allJumpPads.load(plugin);
	}

	void disable() {
		_allJumpPads.save();
	}

	void maybeJumpUp(Player player, Location location) {
		JumpPadInfo info = _allJumpPads.getByLocation(location);
		if (info == null) {
			mustReadRules(player, true);
			playerCanJump(player, true);
			return;
		}
		if (!hasReadRules(player)) {
			maybeTellPlayerToReadTheRules(player);
			return;
		}
		if (canPlayerJump(player)) return;

		jumpUp(player, info);
	}

	private void jumpUp(Player player, JumpPadInfo info) {
		Vector upwards = new Vector(0.0, info.getVelocity().getY(), 0.0);
		player.setVelocity(upwards);
		_inAirPlayers.put(player, info);
	}

	boolean maybeShootForward(Player player, Location from, Location to) {
		if (!_inAirPlayers.containsKey(player)) return false;
		if (to.getY() >= from.getY()) return false;
		shootForward(player);
		return true;
	}

	private void shootForward(Player player) {
		JumpPadInfo info = _inAirPlayers.get(player);
		_inAirPlayers.remove(player);
		Vector velocity = new Vector(info.getVelocity().getX(), player.getVelocity().getY(), info.getVelocity().getZ());
		player.setVelocity(velocity);
	}

	private void maybeTellPlayerToReadTheRules(Player player) {
		if (shouldReadRules(player)) {
			player.sendMessage("Please read the global rules (/rules) to get access to the jump pads.");
			mustReadRules(player, true);
		}
	}

	void addCommand(Player player, String[] args)
	{
		if (!verifyPermission(player, "jumppad.add")) return;
		if (!arrayLengthIsWithinInterval(args, 3, 4)) {
			player.sendMessage(ADD_COMMAND);
			return;
		}

		String name = args[1];
		if (!verifyNameIsNew(player, name)) return;	

		double upSpeed = 0.0;
		double forwardSpeed = 0.0;
		try 
		{
			upSpeed = Double.parseDouble(args[2]);
			if (args.length > 3)
			{
				forwardSpeed = Double.parseDouble(args[3]);
			}		
		} catch (Exception e) {
			player.sendMessage(ADD_COMMAND);
			return;
		}

		createOrUpdateJumpPad(player, name, upSpeed, forwardSpeed);
	}

	private boolean verifyNameIsNew(Player player, String name) {
		JumpPadInfo info = _allJumpPads.getByName(name);
		if (info != null)
		{
			player.sendMessage("Jumppad already exists: " + name);
			return true;		
		}
		return true;
	}

	private void createOrUpdateJumpPad(Player player, String name, double upSpeed, double forwardSpeed) {
		Location location;
		Vector velocityVector;
		location = player.getLocation();
		velocityVector = convertToVelocityVector(location, upSpeed, forwardSpeed);
		try {
			JumpPadInfo newInfo = new JumpPadInfo(name, location, velocityVector, player.getUniqueId(), player.getName());
			_allJumpPads.add(newInfo);
			if (player != null) {
				_noJumpPlayers.put(player, player);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}

	private Vector convertToVelocityVector(Location location, double upSpeed, double forwardSpeed) {
		double yaw = location.getYaw();
		double rad = yaw*Math.PI/180.0;
		double vectorX = -Math.sin(rad)*forwardSpeed;
		double vectorY = upSpeed;
		double vectorZ = Math.cos(rad)*forwardSpeed;
		Vector jumpVector = new Vector(vectorX, vectorY, vectorZ);
		return jumpVector;
	}

	void editCommand(Player player, String[] args)
	{
		if (!verifyPermission(player, "jumppad.edit")) return;
		JumpPadInfo info = _allJumpPads.getByLocation(player.getLocation());
		if (info == null) {
			player.sendMessage("You must go to a jumppad before you edit the jumppad. Use /jumppad goto <name>.");	
			return;
		}
		if (!arrayLengthIsWithinInterval(args, 2, 3)) {
			player.sendMessage(EDIT_COMMAND);
			return;
		}

		double upSpeed = 0.0;
		double forwardSpeed = 0.0;
		try 
		{
			upSpeed = Double.parseDouble(args[1]);

			if (args.length > 2)
			{
				forwardSpeed = Double.parseDouble(args[2]);
			}		
		} catch (Exception e) {
			player.sendMessage(EDIT_COMMAND);
			return;
		}

		createOrUpdateJumpPad(player, info.getName(), upSpeed, forwardSpeed);
	}

	void removeCommand(Player player, String[] args)
	{
		if (!verifyPermission(player, "jumppad.remove")) return;
		if (!arrayLengthIsWithinInterval(args, 2, 2)) {
			player.sendMessage(REMOVE_COMMAND);
			return;
		}
		String name = args[1];
		JumpPadInfo info = _allJumpPads.getByName(name);
		if (info == null)
		{
			player.sendMessage("Unknown jumppad: " + name);
			return;	
		}
		_allJumpPads.remove(info);
	}

	void gotoCommand(Player player, String[] args)
	{
		if (!verifyPermission(player, "jumppad.goto")) return;
		if (args.length < 2) {
			player.sendMessage(GOTO_COMMAND);
			return;
		}
		String name = args[1];
		JumpPadInfo info = _allJumpPads.getByName(name);
		if (info == null)
		{
			player.sendMessage("Unknown jumppad: " + name);
			return;			
		}
		player.teleport(info.getLocation());
		// Temporarily disable jump for this player to avoid an immediate jump at the jump pad
		playerCanJump(player, false);
	}

	void listCommand(Player player)
	{
		if (!verifyPermission(player, "jumppad.list")) return;

		player.sendMessage("Jump pads:");
		for (JumpPadInfo info : _allJumpPads.getAll()) {
			player.sendMessage(info.toString());
		}
	}

	private boolean verifyPermission(Player player, String permission)
	{
		if (player.hasPermission(permission)) return true;
		player.sendMessage("You must have permission " + permission);
		return false;
	}

	static Vector calculateVelocity(Vector from, Vector to, int heightGain)
	{
		// Gravity of a potion
		double gravity = 0.115;

		// Block locations
		int endGain = to.getBlockY() - from.getBlockY();
		double horizDist = Math.sqrt(distanceSquared(from, to));

		// Height gain
		int gain = heightGain;

		double maxGain = gain > (endGain + gain) ? gain : (endGain + gain);

		// Solve quadratic equation for velocity
		double a = -horizDist * horizDist / (4 * maxGain);
		double b = horizDist;
		double c = -endGain;

		double slope = -b / (2 * a) - Math.sqrt(b * b - 4 * a * c) / (2 * a);

		// Vertical velocity
		double vy = Math.sqrt(maxGain * gravity);

		// Horizontal velocity
		double vh = vy / slope;

		// Calculate horizontal direction
		int dx = to.getBlockX() - from.getBlockX();
		int dz = to.getBlockZ() - from.getBlockZ();
		double mag = Math.sqrt(dx * dx + dz * dz);
		double dirx = dx / mag;
		double dirz = dz / mag;

		// Horizontal velocity components
		double vx = vh * dirx;
		double vz = vh * dirz;

		return new Vector(vx, vy, vz);
	}

	private static double distanceSquared(Vector from, Vector to)
	{
		double dx = to.getBlockX() - from.getBlockX();
		double dz = to.getBlockZ() - from.getBlockZ();

		return dx * dx + dz * dz;
	}

	void listenToCommands(Player player, String message) {
		if (message.toLowerCase().startsWith(RULES_COMMAND_BEGINNING))
		{
			player.sendMessage("Getting permission");
			player.addAttachment(_plugin, "jumppad.jump", true);
		}
	}

	private boolean canPlayerJump(Player player) {
		return _noJumpPlayers.containsKey(player);
	}

	private void playerCanJump(Player player, boolean canJump) {
		if (canJump){
			if (!canPlayerJump(player)) {
				_noJumpPlayers.put(player, player);
			}
		} else {
			if (canPlayerJump(player)) {
				_noJumpPlayers.remove(player);
			}
		}
	}

	private boolean shouldReadRules(Player player) {
		return !_informedPlayers.containsKey(player);
	}

	private void mustReadRules(Player player, boolean mustReadRules) {
		if (mustReadRules) {
			if (!shouldReadRules(player)) {
				_informedPlayers.put(player, player);
			}
		} else {
			if (shouldReadRules(player)) {
				_informedPlayers.remove(player);
			}
		}
	}

	private boolean hasReadRules(Player player) {
		return player.hasPermission("jumppad.jump");
	}

	private boolean arrayLengthIsWithinInterval(Object[] args, int min, int max) {
		return (args.length >= min) && (args.length <= max);
	}
}
