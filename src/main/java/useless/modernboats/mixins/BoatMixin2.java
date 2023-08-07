package useless.modernboats.mixins;

import com.mojang.nbt.CompoundTag;
import net.minecraft.client.entity.player.EntityPlayerSP;
import net.minecraft.client.input.Input;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.vehicle.EntityBoat;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.util.phys.AABB;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = EntityBoat.class, remap = false)
public class BoatMixin2 extends Entity {

    @Shadow
    public int boatTimeSinceHit;
    @Shadow
    public int boatCurrentDamage;

    public BoatMixin2(World world) {
        super(world);
    }

    @Shadow
    protected void init() {

    }

    @Shadow
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Shadow
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Shadow
    public void lerpMotion(double xd, double yd, double zd) {

    }
    @Shadow
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int i) {}

    /*@Unique
    private int tick = 0;*/

    @Unique
    private double rotationVelocity = 0;
    @Unique
    private boolean isMovingBackward = false;
    @Unique
    private double vectorMagnitude = 0;

    /**
     * @author Useless
     * @reason Modify boat behavior for single-player but retain original for multiplayer
     */
    @Overwrite
    public void tick(){
        if (!world.isClientSide){
            tickSinglePlayer();;
        }
        else{
            tickServer();
        }
    }
    @Unique
    public void tickSinglePlayer() {
        //tick++;
        super.tick();

        if (this.boatTimeSinceHit > 0) {
            --this.boatTimeSinceHit;
        }

        if (this.boatCurrentDamage > 0) {
            --this.boatCurrentDamage;
        }

        boolean attemptingMotion = false;
        Input passangerInput;
        if (passenger != null){
            // Establish access to passengers inputs
            passangerInput = ((EntityPlayerSP)passenger).input;

            // Acceleration for turning
            if (Math.abs(passangerInput.moveStrafe) > 0.1){
                double rotationAcceleration = 1;
                rotationVelocity += rotationAcceleration * -passangerInput.moveStrafe;
                vectorMagnitude *= 0.95;
            }
            else {
                // Speed decay while not turning
                rotationVelocity *= .75;
            }

            double rotationSpeed = 8;
            rotationVelocity = bindToRange(rotationVelocity, this.onGround ? rotationSpeed * 0.5 : rotationSpeed);

            // Rotate boat based of player strafe inputs
            this.yRot +=  rotationVelocity;

            // Movement speed when moving forward
            double boatAcceleration = 0.01;
            if (passangerInput.moveForward > 0.1){
                isMovingBackward = false;
                attemptingMotion = true;
                vectorMagnitude += -passangerInput.moveForward * boatAcceleration;
            }
            // Movement speed when moving backward
            else if (passangerInput.moveForward < -0.1) {
                isMovingBackward = true;
                attemptingMotion = true;
                double backwardsAccelerationModifier = 0.5;
                vectorMagnitude += -passangerInput.moveForward * ((vectorMagnitude < 0) ? 1 : backwardsAccelerationModifier) * boatAcceleration;
            }

            if (passenger.removed){
                passenger = null;
            }
        }

        // Caps boat speed
        if (vectorMagnitude < 0) {
            double forwardsMaxSpeed = 0.8;
            vectorMagnitude = bindToRange(vectorMagnitude, forwardsMaxSpeed);
        }
        else {
            double backwardsMaxSpeed = 0.3;
            vectorMagnitude = bindToRange(vectorMagnitude, backwardsMaxSpeed);
        }

        buoyancy();

        this.move(xd + vectorMagnitude * Math.cos(Math.toRadians(yRot)), yd, zd + vectorMagnitude * Math.sin(Math.toRadians(yRot)));
        generateSplashes();

        vectorMagnitude = realSpeed() * Math.signum(vectorMagnitude);

        /*if (tick % 5 == 0){
            ModernBoats.LOGGER.info(String.format("VectorMagnitude:%s | RealSpeed:%s", vectorMagnitude, realSpeed()));
        }*/


        // Speed Decay
        if (!onGround){
            vectorMagnitude *= attemptingMotion ? 1 : 0.95;

            xd *= 0.95;
            zd *= 0.95;
            yd *= 0.95;
        }
        else {
            vectorMagnitude *= attemptingMotion ? 0.75 : 0.5;

            xd *= 0.5;
            zd *= 0.5;
            yd *= 0.5;
        }

        // Braking aid
        if (isMovingBackward && vectorMagnitude < 0){
            vectorMagnitude *= 0.8;
        }

        pushBoat();
        breakSnow();


    }

    @Unique
    public void tickServer(){
        super.tick();
        if (this.boatTimeSinceHit > 0) {
            --this.boatTimeSinceHit;
        }

        if (this.boatCurrentDamage > 0) {
            --this.boatCurrentDamage;
        }

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        int i = 5;
        double d = 0.0;

        for (int j = 0; j < i; ++j) {
            double d5 = this.bb.minY + (this.bb.maxY - this.bb.minY) * (double) j / (double) i - 0.125;
            double d9 = this.bb.minY + (this.bb.maxY - this.bb.minY) * (double) (j + 1) / (double) i - 0.125;
            AABB axisalignedbb = AABB.getBoundingBoxFromPool(this.bb.minX, d5, this.bb.minZ, this.bb.maxX, d9, this.bb.maxZ);
            if (this.world.isAABBInMaterial(axisalignedbb, Material.water)) {
                d += 1.0 / (double) i;
            }
        }

        if (d < 1.0) {
            double d3 = d * 2.0 - 1.0;
            this.yd += 0.04F * d3;
        } else {
            if (this.yd < 0.0) {
                this.yd /= 2.0;
            }

            this.yd += 0.007F;
        }

        if (this.passenger != null) {
            this.xd += this.passenger.xd * 0.4;
            this.zd += this.passenger.zd * 0.4;
        }

        double maxSpeed = 0.8;
        if (this.xd < -maxSpeed) {
            this.xd = -maxSpeed;
        }

        if (this.xd > maxSpeed) {
            this.xd = maxSpeed;
        }

        if (this.zd < -maxSpeed) {
            this.zd = -maxSpeed;
        }

        if (this.zd > maxSpeed) {
            this.zd = maxSpeed;
        }

        if (this.onGround) {
            this.xd *= 0.5;
            this.yd *= 0.5;
            this.zd *= 0.5;
        }

        this.move(this.xd, this.yd, this.zd);
        double d8 = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
        if (d8 > 0.15) {
            double d12 = Math.cos((double) this.yRot * Math.PI / 180.0);
            double d15 = Math.sin((double) this.yRot * Math.PI / 180.0);

            for (int i1 = 0; (double) i1 < 1.0 + d8 * 60.0; ++i1) {
                double d18 = (double) (this.random.nextFloat() * 2.0F - 1.0F);
                double d20 = (double) (this.random.nextInt(2) * 2 - 1) * 0.7;
                if (this.random.nextBoolean()) {
                    double d21 = this.x - d12 * d18 * 0.8 + d15 * d20;
                    double d23 = this.z - d15 * d18 * 0.8 - d12 * d20;
                    this.world.spawnParticle("splash", d21, this.y - 0.125, d23, this.xd, this.yd, this.zd);
                } else {
                    double d22 = this.x + d12 + d15 * d18 * 0.7;
                    double d24 = this.z + d15 - d12 * d18 * 0.7;
                    this.world.spawnParticle("splash", d22, this.y - 0.125, d24, this.xd, this.yd, this.zd);
                }
            }
        }

        this.xd *= 0.99F;
        this.yd *= 0.95F;
        this.zd *= 0.99F;
        this.xRot = 0.0F;
        double d13 = (double) this.yRot;
        double d16 = this.xo - this.x;
        double d17 = this.zo - this.z;
        if (d16 * d16 + d17 * d17 > 0.001) {
            d13 = (double) ((float) (Math.atan2(d17, d16) * 180.0 / Math.PI));
        }

        double d19 = d13 - (double) this.yRot;

        while (d19 >= 180.0) {
            d19 -= 360.0;
        }

        while (d19 < -180.0) {
            d19 += 360.0;
        }

        if (d19 > 20.0) {
            d19 = 20.0;
        }

        if (d19 < -20.0) {
            d19 = -20.0;
        }

        this.yRot = (float) ((double) this.yRot + d19);
        this.setRot(this.yRot, this.xRot);
        List list = this.world.getEntitiesWithinAABBExcludingEntity(this, this.bb.expand(0.2F, 0.0, 0.2F));
        if (list != null && list.size() > 0) {
            for (int j1 = 0; j1 < list.size(); ++j1) {
                Entity entity = (Entity) list.get(j1);
                if (entity != this.passenger && entity.isPushable() && entity instanceof EntityBoat) {
                    entity.push(this);
                }
            }
        }

        for (int k1 = 0; k1 < 4; ++k1) {
            int l1 = MathHelper.floor_double(this.x + ((double) (k1 % 2) - 0.5) * 0.8);
            int i2 = MathHelper.floor_double(this.y);
            int j2 = MathHelper.floor_double(this.z + ((double) (k1 / 2) - 0.5) * 0.8);
            if (this.world.getBlockId(l1, i2, j2) == Block.layerSnow.id) {
                this.world.setBlockWithNotify(l1, i2, j2, 0);
            }
        }

        if (this.passenger != null && this.passenger.removed) {
            this.passenger = null;
        }
    }
    @Unique
    private void buoyancy(){
        int i = 5;
        double d = 0.0;

        for(int j = 0; j < i; ++j) {
            double d5 = this.bb.minY + (this.bb.maxY - this.bb.minY) * (double)j / (double)i - 0.125;
            double d9 = this.bb.minY + (this.bb.maxY - this.bb.minY) * (double)(j + 1) / (double)i - 0.125;
            AABB axisalignedbb = AABB.getBoundingBoxFromPool(this.bb.minX, d5, this.bb.minZ, this.bb.maxX, d9, this.bb.maxZ);
            if (this.world.isAABBInMaterial(axisalignedbb, Material.water)) {
                d += 1.0 / (double)i;
            }
        }
        if (d < 1.0) {
            double d3 = d * 2.0 - 1.0;
            this.yd += 0.04F * d3;
        } else {
            if (this.yd < 0.0) {
                this.yd /= 2.0;
            }

            this.yd += 0.007F;
        }
    }

    @Unique
    private void generateSplashes(){
        double speed = realSpeed();
        if (speed > 0.15) {
            double xComp = Math.cos(Math.toRadians(this.yRot));
            double yComp = Math.sin(Math.toRadians(this.yRot));

            for(int i1 = 0; (double)i1 < 1.0 + speed * 60.0; ++i1) {
                double d18 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
                double d20 = (double)(this.random.nextInt(2) * 2 - 1) * 0.7;
                if (this.random.nextBoolean()) {
                    double d21 = this.x - xComp * d18 * 0.8 + yComp * d20;
                    double d23 = this.z - yComp * d18 * 0.8 - xComp * d20;
                    this.world.spawnParticle("splash", d21, this.y - 0.125, d23, this.xd, this.yd, this.zd);
                } else {
                    double d22 = this.x + xComp + yComp * d18 * 0.7;
                    double d24 = this.z + yComp - xComp * d18 * 0.7;
                    this.world.spawnParticle("splash", d22, this.y - 0.125, d24, this.xd, this.yd, this.zd);
                }
            }
        }
    }

    @Unique
    private void breakSnow(){
        for(int k1 = 0; k1 < 4; ++k1) {
            int l1 = MathHelper.floor_double(this.x + ((double)(k1 % 2) - 0.5) * 0.8);
            int i2 = MathHelper.floor_double(this.y);
            int j2 = MathHelper.floor_double(this.z + ((double)(k1 / 2) - 0.5) * 0.8);
            if (this.world.getBlockId(l1, i2, j2) == Block.layerSnow.id) {
                this.world.setBlockWithNotify(l1, i2, j2, 0);
            }
        }
    }

    @Unique
    private double bindToRange(double value, double absMinMax){
        return Math.min(Math.max(value, -absMinMax), absMinMax);
    }

    @Unique
    private double realSpeed(){
        return Math.sqrt(Math.pow(this.x - this.xOld,2) + Math.pow(this.z - this.zOld,2));
    }

    @Unique
    private void pushBoat(){
        this.yRot = (float)((double)this.yRot);
        this.setRot(this.yRot, this.xRot);
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this, this.bb.expand(0.2F, 0.0, 0.2F));
        if (list != null && list.size() > 0) {
            for (Entity value : list) {
                if (value != this.passenger && value.isPushable() && value instanceof EntityBoat) {
                    value.push(this);
                }
            }
        }
    }
}
