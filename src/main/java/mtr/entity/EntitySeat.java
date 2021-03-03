package mtr.entity;

import mtr.MTR;
import mtr.data.Route;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("EntityConstructor")
public class EntitySeat extends Entity {

	public int ridingCar;
	public float ridingXOffset, ridingZOffset;

	private float ridingXOffsetOld, ridingZOffsetOld;
	private int worldTimeUpdated;

	private PlayerEntity player;
	private int seatCoolDown;

	private int clientInterpolationSteps;
	private double clientX;
	private double clientY;
	private double clientZ;

	public static final int DETAIL_RADIUS = 32;
	public static final int MAX_SEAT_COOL_DOWN = 2;

	private static final TrackedData<Optional<UUID>> PLAYER_ID = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Integer> RIDING_CAR = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> RIDING_X_OFFSET = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> RIDING_Z_OFFSET = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> RIDING_X_OFFSET_OLD = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> RIDING_Z_OFFSET_OLD = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> WORLD_TIME_UPDATED = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SCHEDULE_TIME = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> ROUTE_ID = DataTracker.registerData(EntitySeat.class, TrackedDataHandlerRegistry.INTEGER);
	private static final String KEY_PLAYER_ID = "player_id";

	public EntitySeat(EntityType<?> type, World world) {
		super(type, world);
		ignoreCameraFrustum = true;
	}

	public EntitySeat(World world, double x, double y, double z) {
		this(MTR.SEAT, world);
		updatePosition(x, y, z);
		setVelocity(Vec3d.ZERO);
		prevX = x;
		prevY = y;
		prevZ = z;
	}

	@Override
	public void tick() {
		getPlayer();

		if (world.isClient) {
			if (player != null) {
				if (hasPassenger(player)) {
					if (clientInterpolationSteps > 0) {
						double d = getX() + (clientX - getX()) / (double) clientInterpolationSteps;
						double e = getY() + (clientY - getY()) / (double) clientInterpolationSteps;
						double f = getZ() + (clientZ - getZ()) / (double) clientInterpolationSteps;
						--clientInterpolationSteps;
						updatePosition(d, e, f);
					} else {
						refreshPosition();
					}
				} else {
					final Vec3d newPos = player.getPos().add(player.getVelocity());
					updatePosition(newPos.x, newPos.y, newPos.z);
				}
			}
		} else {
			if (player != null) {
				if (!hasPassenger(player)) {
					updatePosition(player.getX(), player.getY(), player.getZ());
				}
				if (seatCoolDown > 0) {
					seatCoolDown--;
				} else {
					removeAllPassengers();
				}
			}
			if (player == null || player.isDead()) {
				kill();
			}
		}
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
		clientX = x;
		clientY = y;
		clientZ = z;
		clientInterpolationSteps = interpolationSteps + 2;
	}

	@Override
	public Packet<?> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public boolean shouldRender(double distance) {
		return true;
	}

	@Override
	public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
		return true;
	}

	@Override
	protected void initDataTracker() {
		dataTracker.startTracking(PLAYER_ID, null);
		dataTracker.startTracking(RIDING_CAR, 0);
		dataTracker.startTracking(RIDING_X_OFFSET, 0F);
		dataTracker.startTracking(RIDING_Z_OFFSET, 0F);
		dataTracker.startTracking(RIDING_X_OFFSET_OLD, 0F);
		dataTracker.startTracking(RIDING_Z_OFFSET_OLD, 0F);
		dataTracker.startTracking(WORLD_TIME_UPDATED, 0);
		dataTracker.startTracking(SCHEDULE_TIME, 0);
		dataTracker.startTracking(ROUTE_ID, 0);
	}

	@Override
	protected void readCustomDataFromTag(CompoundTag tag) {
		setPlayerId(tag.getUuid(KEY_PLAYER_ID));
	}

	@Override
	protected void writeCustomDataToTag(CompoundTag tag) {
		tag.putUuid(KEY_PLAYER_ID, getPlayerId());
	}

	public PlayerEntity getPlayer() {
		if (player == null) {
			player = world.getPlayerByUuid(getPlayerId());
		}
		return player;
	}

	public void setPlayerId(UUID playerId) {
		if (dataTracker != null && playerId != null) {
			dataTracker.set(PLAYER_ID, Optional.of(playerId));
		}
	}

	public void setRidingCar(int ridingCar) {
		if (dataTracker != null) {
			dataTracker.set(RIDING_CAR, ridingCar);
		}
		this.ridingCar = ridingCar;
	}

	public void updateRidingOffset(int worldTime) {
		if (dataTracker != null) {
			dataTracker.set(RIDING_X_OFFSET, ridingXOffset);
			dataTracker.set(RIDING_Z_OFFSET, ridingZOffset);
			dataTracker.set(RIDING_X_OFFSET_OLD, ridingXOffsetOld);
			dataTracker.set(RIDING_Z_OFFSET_OLD, ridingZOffsetOld);
			dataTracker.set(WORLD_TIME_UPDATED, worldTime);
		}
		ridingXOffsetOld = ridingXOffset;
		ridingZOffsetOld = ridingZOffset;
	}

	public void setScheduleTime(int scheduleTime) {
		if (dataTracker != null) {
			dataTracker.set(SCHEDULE_TIME, scheduleTime);
		}
	}

	public void setRouteId(int routeId) {
		if (dataTracker != null) {
			dataTracker.set(ROUTE_ID, routeId);
		}
	}

	public int getRidingCar() {
		return dataTracker == null ? 0 : dataTracker.get(RIDING_CAR);
	}

	public Vec3d getRidingOffset(float worldTime) {
		if (dataTracker == null) {
			return Vec3d.ZERO;
		} else {
			ridingXOffset = dataTracker.get(RIDING_X_OFFSET);
			ridingXOffsetOld = dataTracker.get(RIDING_X_OFFSET_OLD);
			ridingZOffset = dataTracker.get(RIDING_Z_OFFSET);
			ridingZOffsetOld = dataTracker.get(RIDING_Z_OFFSET_OLD);
			worldTimeUpdated = dataTracker.get(WORLD_TIME_UPDATED);
			final float tickDelta = (worldTime - worldTimeUpdated) % Route.TICKS_PER_DAY;
			return new Vec3d(MathHelper.lerp(tickDelta, ridingXOffsetOld, ridingXOffset), 0, MathHelper.lerp(tickDelta, ridingZOffsetOld, ridingZOffset));
		}
	}

	public int getScheduleTime() {
		return dataTracker == null ? 0 : dataTracker.get(SCHEDULE_TIME);
	}

	public int getRouteId() {
		return dataTracker == null ? 0 : dataTracker.get(ROUTE_ID);
	}

	public boolean getIsRiding() {
		return seatCoolDown > 0;
	}

	public void resetSeatCoolDown() {
		seatCoolDown = MAX_SEAT_COOL_DOWN;
	}

	private UUID getPlayerId() {
		if (dataTracker == null) {
			return new UUID(0, 0);
		} else {
			return dataTracker.get(PLAYER_ID).orElse(new UUID(0, 0));
		}
	}
}
