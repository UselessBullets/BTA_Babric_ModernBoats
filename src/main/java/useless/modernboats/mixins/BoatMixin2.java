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

    @Override
    public void tick() {
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
