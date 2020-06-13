package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.citadel.server.entity.EntityPropertiesHandler;
import com.github.alexthe666.iceandfire.IceAndFire;
import com.github.alexthe666.iceandfire.block.IafBlockRegistry;
import com.github.alexthe666.iceandfire.entity.ai.PixieAIFlee;
import com.github.alexthe666.iceandfire.entity.ai.PixieAIFollowOwner;
import com.github.alexthe666.iceandfire.entity.ai.PixieAIPickupItem;
import com.github.alexthe666.iceandfire.entity.ai.PixieAISteal;
import com.github.alexthe666.iceandfire.entity.props.StoneEntityProperties;
import com.github.alexthe666.iceandfire.entity.tile.TileEntityPixieHouse;
import com.github.alexthe666.iceandfire.message.MessageUpdatePixieHouse;
import com.github.alexthe666.iceandfire.misc.IafSoundRegistry;
import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Random;

public class EntityPixie extends TameableEntity {

    public static final float[][] PARTICLE_RGB = new float[][]{new float[]{1F, 0.752F, 0.792F}, new float[]{0.831F, 0.662F, 1F}, new float[]{0.513F, 0.843F, 1F}, new float[]{0.654F, 0.909F, 0.615F}, new float[]{0.996F, 0.788F, 0.407F}};
    private static final DataParameter<Integer> COLOR = EntityDataManager.createKey(EntityPixie.class, DataSerializers.VARINT);
    public Effect[] positivePotions = new Effect[]{Effects.STRENGTH, Effects.JUMP_BOOST, Effects.SPEED, Effects.LUCK, Effects.HASTE};
    public Effect[] negativePotions = new Effect[]{Effects.WEAKNESS, Effects.NAUSEA, Effects.SLOWNESS, Effects.UNLUCK, Effects.MINING_FATIGUE};
    public boolean slowSpeed = false;
    public int ticksUntilHouseAI;
    private BlockPos housePos;
    private PixieAIFlee aiFlee;
    private PixieAISteal aiTempt;

    public EntityPixie(EntityType type, World worldIn) {
        super(type, worldIn);
        this.moveController = new EntityPixie.AIMoveControl(this);
        this.experienceValue = 3;
        this.setDropChance(EquipmentSlotType.MAINHAND, 0F);
    }

    public static BlockPos getPositionRelativetoGround(Entity entity, World world, double x, double z, Random rand) {
        BlockPos pos = new BlockPos(x, entity.getPosY(), z);
        for (int yDown = 0; yDown < 3; yDown++) {
            if (!world.isAirBlock(pos.down(yDown))) {
                return pos.up(yDown);
            }
        }
        return pos;
    }

    public static BlockPos findAHouse(Entity entity, World world) {
        for (int xSearch = -10; xSearch < 10; xSearch++) {
            for (int ySearch = -10; ySearch < 10; ySearch++) {
                for (int zSearch = -10; zSearch < 10; zSearch++) {
                    if (world.getTileEntity(entity.getPosition().add(xSearch, ySearch, zSearch)) != null && world.getTileEntity(entity.getPosition().add(xSearch, ySearch, zSearch)) instanceof TileEntityPixieHouse) {
                        TileEntityPixieHouse house = (TileEntityPixieHouse) world.getTileEntity(entity.getPosition().add(xSearch, ySearch, zSearch));
                        if (!house.hasPixie) {
                            return entity.getPosition().add(xSearch, ySearch, zSearch);
                        }
                    }
                }
            }
        }
        return entity.getPosition();
    }

    protected int getExperiencePoints(PlayerEntity player) {
        return 3;
    }

    @Override
    protected void registerAttributes() {
        super.registerAttributes();
        getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25);
        getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10);
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        StoneEntityProperties properties = EntityPropertiesHandler.INSTANCE.getProperties(this, StoneEntityProperties.class);

        if (!this.world.isRemote && this.getRNG().nextInt(3) == 0 && this.getHeldItem(Hand.MAIN_HAND) != ItemStack.EMPTY && !properties.isStone()) {
            this.entityDropItem(this.getHeldItem(Hand.MAIN_HAND), 0);
            this.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);
            return true;
        }
        if (this.isOwnerClose() && (source == DamageSource.FALLING_BLOCK || source == DamageSource.IN_WALL || this.getOwner() != null && source.getTrueSource() == this.getOwner())) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    public void onDeath(DamageSource cause) {
        if (!this.world.isRemote && !this.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
            this.entityDropItem(this.getHeldItem(Hand.MAIN_HAND), 0);
            this.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        super.onDeath(cause);
        //if (cause.getTrueSource() instanceof PlayerEntity) {
        //	((PlayerEntity) cause.getTrueSource()).addStat(ModAchievements.killPixie);
        //}
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.getDataManager().register(COLOR, Integer.valueOf(0));
    }

    protected void collideWithEntity(Entity entityIn) {
        if (this.getOwner() != entityIn) {
            entityIn.applyEntityCollision(this);
        }
    }

    protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    public boolean processInteract(PlayerEntity player, Hand hand) {
        if (player.getHeldItem(hand).interactWithEntity(player, this, hand)) {
            return true;
        }
        if (this.isOwner(player)) {
            if (player.getHeldItem(hand).getItem() == Items.SUGAR && this.getHealth() < this.getMaxHealth()) {
                this.heal(5);
                player.getHeldItem(hand).shrink(1);
                this.playSound(IafSoundRegistry.PIXIE_TAUNT, 1F, 1F);
                return true;
            } else {
                this.setSitting(!this.isSitting());
                return true;
            }
        } else if (player.getHeldItem(hand).getItem() == Item.getItemFromBlock(IafBlockRegistry.JAR_EMPTY) && !this.isTamed()) {
            if (!player.isCreative()) {
                player.getHeldItem(hand).shrink(1);
            }
            Block jar = IafBlockRegistry.JAR_PIXIE_0;
            switch (this.getColor()) {
                case 0:
                    jar = IafBlockRegistry.JAR_PIXIE_0;
                    break;
                case 1:
                    jar = IafBlockRegistry.JAR_PIXIE_1;
                    break;
                case 2:
                    jar = IafBlockRegistry.JAR_PIXIE_2;
                    break;
                case 3:
                    jar = IafBlockRegistry.JAR_PIXIE_3;
                    break;
                case 4:
                    jar = IafBlockRegistry.JAR_PIXIE_4;
                    break;
            }
            ItemStack stack = new ItemStack(jar, 1);
            if (!world.isRemote) {
                if (!this.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
                    this.entityDropItem(this.getHeldItem(Hand.MAIN_HAND), 0.0F);
                }
                this.entityDropItem(stack, 0.0F);
            }
            //player.addStat(ModAchievements.jarPixie);
            this.remove();
        }
        return super.processInteract(player, hand);
    }

    public void flipAI(boolean flee) {
        if (flee) {
            this.goalSelector.removeGoal(aiTempt);
            this.goalSelector.addGoal(3, aiFlee);
        } else {
            this.goalSelector.removeGoal(aiFlee);
            this.goalSelector.addGoal(3, aiTempt);
        }
    }

    public void fall(float distance, float damageMultiplier) {
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new PixieAIFollowOwner(this, 1.0D, 2.0F, 4.0F));
        this.goalSelector.addGoal(1, new PixieAIPickupItem(this, false));

        this.goalSelector.addGoal(2, aiTempt = new PixieAISteal(this, 1.0D));

        this.goalSelector.addGoal(2, aiFlee = new PixieAIFlee(this, PlayerEntity.class, 10, new Predicate<PlayerEntity>() {
            @Override
            public boolean apply(@Nullable PlayerEntity entity) {
                return true;
            }
        }));
        this.goalSelector.addGoal(3, new AIMoveRandom());
        this.goalSelector.addGoal(4, new AIEnterHouse());
        this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(7, new LookRandomlyGoal(this));
        this.goalSelector.removeGoal(aiFlee);
    }

    @Override
    @Nullable
    public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
        spawnDataIn = super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
        this.setColor(this.rand.nextInt(5));
        this.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);
        return spawnDataIn;
    }

    private boolean isBeyondHeight() {
        if (this.getPosY() > this.world.getHeight()) {
            return true;
        }
        BlockPos height = this.world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(this));
        int maxY = 20 + height.getY();
        return this.getPosY() > maxY;
    }

    public void livingTick() {
        super.livingTick();
        if (!this.isSitting() && !this.isBeyondHeight()) {
            this.setMotion(this.getMotion().add(0, 0.08, 0));
        } else {
        }
        if (world.isRemote) {
            IceAndFire.PROXY.spawnParticle("if_pixie", this.getPosX() + (double) (this.rand.nextFloat() * this.getWidth() * 2F) - (double) this.getWidth(), this.getPosY() + (double) (this.rand.nextFloat() * this.getHeight()), this.getPosZ() + (double) (this.rand.nextFloat() * this.getWidth() * 2F) - (double) this.getWidth(), PARTICLE_RGB[this.getColor()][0], PARTICLE_RGB[this.getColor()][1], PARTICLE_RGB[this.getColor()][2]);
        }
        if (ticksUntilHouseAI > 0) {
            ticksUntilHouseAI--;
        }
        if(!world.isRemote){
            if (housePos != null && this.getDistanceSq(new Vec3d(housePos).add(0.5D, 0.5D, 0.5D)) < 1.5F && world.getTileEntity(housePos) != null && world.getTileEntity(housePos) instanceof TileEntityPixieHouse) {
                TileEntityPixieHouse house = (TileEntityPixieHouse)world.getTileEntity(housePos);
                if (house.hasPixie) {
                    this.housePos = null;
                } else {
                    house.hasPixie = true;
                    house.pixieType = this.getColor();
                    house.pixieItems.set(0, this.getHeldItem(Hand.MAIN_HAND));
                    house.tamedPixie = this.isTamed();
                    house.pixieOwnerUUID = this.getOwnerId();
                    IceAndFire.sendMSGToAll(new MessageUpdatePixieHouse(housePos.toLong(), true, this.getColor()));
                    this.remove();
                }
            }
        }
        if (this.getOwner() != null && this.isOwnerClose() && this.ticksExisted % 80 == 0) {
            this.getOwner().addPotionEffect(new EffectInstance(positivePotions[this.getColor()], 100, 0, false, false));
        }
        //PlayerEntity player = world.getClosestPlayerToEntity(this, 25);
        //if (player != null) {
        //	player.addStat(ModAchievements.findPixie);
        //}
    }

    public int getColor() {
        return MathHelper.clamp(this.getDataManager().get(COLOR).intValue(), 0, 4);
    }

    public void setColor(int color) {
        this.getDataManager().set(COLOR, color);
    }

    @Override
    public void readAdditional(CompoundNBT compound) {
        this.setColor(compound.getInt("Color"));
        super.readAdditional(compound);
    }

    @Override
    public void writeAdditional(CompoundNBT compound) {
        compound.putInt("Color", this.getColor());
        super.writeAdditional(compound);
    }

    @Nullable
    @Override
    public AgeableEntity createChild(AgeableEntity ageable) {
        return null;
    }

    public BlockPos getHousePos() {
        return housePos;
    }

    public boolean isOwnerClose() {
        return this.isTamed() && this.getOwner() != null && this.getDistanceSq(this.getOwner()) < 100;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        return IafSoundRegistry.PIXIE_IDLE;
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
        return IafSoundRegistry.PIXIE_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return IafSoundRegistry.PIXIE_DIE;
    }

    class AIMoveControl extends MovementController {
        public AIMoveControl(EntityPixie pixie) {
            super(pixie);
            this.speed = 0.75F;
        }

        public void tick() {
            if (EntityPixie.this.slowSpeed) {
                this.speed = 2F;
            }
            if (this.action == MovementController.Action.MOVE_TO) {
                if (EntityPixie.this.collidedHorizontally) {
                    EntityPixie.this.rotationYaw += 180.0F;
                    this.speed = 0.1F;
                    BlockPos target = EntityPixie.getPositionRelativetoGround(EntityPixie.this, EntityPixie.this.world, EntityPixie.this.getPosX() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.getPosZ() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.rand);
                    this.posX = target.getX();
                    this.posY = target.getY();
                    this.posZ = target.getZ();
                }
                double d0 = this.posX - EntityPixie.this.getPosX();
                double d1 = this.posY - EntityPixie.this.getPosY();
                double d2 = this.posZ - EntityPixie.this.getPosZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                d3 = MathHelper.sqrt(d3);

                if (d3 < EntityPixie.this.getBoundingBox().getAverageEdgeLength()) {
                    this.action = MovementController.Action.WAIT;
                    EntityPixie.this.setMotion(EntityPixie.this.getMotion().mul(0.5D, 0.5D, 0.5D));
                } else {
                    EntityPixie.this.setMotion(EntityPixie.this.getMotion().add(d0 / d3 * 0.05D * this.speed, d1 / d3 * 0.05D * this.speed, d2 / d3 * 0.05D * this.speed));

                    if (EntityPixie.this.getAttackTarget() == null) {
                        EntityPixie.this.rotationYaw = -((float) MathHelper.atan2(EntityPixie.this.getMotion().x, EntityPixie.this.getMotion().z)) * (180F / (float) Math.PI);
                        EntityPixie.this.renderYawOffset = EntityPixie.this.rotationYaw;
                    } else {
                        double d4 = EntityPixie.this.getAttackTarget().getPosX() - EntityPixie.this.getPosX();
                        double d5 = EntityPixie.this.getAttackTarget().getPosZ() - EntityPixie.this.getPosZ();
                        EntityPixie.this.rotationYaw = -((float) MathHelper.atan2(d4, d5)) * (180F / (float) Math.PI);
                        EntityPixie.this.renderYawOffset = EntityPixie.this.rotationYaw;
                    }
                }
            }
        }
    }

    class AIMoveRandom extends Goal {
        BlockPos target;

        public AIMoveRandom() {
            this.setMutexFlags(EnumSet.of(Flag.MOVE));
        }

        public boolean shouldExecute() {
            target = EntityPixie.getPositionRelativetoGround(EntityPixie.this, EntityPixie.this.world, EntityPixie.this.getPosX() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.getPosZ() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.rand);
            return !EntityPixie.this.isOwnerClose() && !EntityPixie.this.isSitting() && isDirectPathBetweenPoints(EntityPixie.this.getPosition(), target) && !EntityPixie.this.getMoveHelper().isUpdating() && EntityPixie.this.rand.nextInt(4) == 0 && EntityPixie.this.housePos == null;
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            return EntityPixie.this.world.rayTraceBlocks(new RayTraceContext(new Vec3d(posVec31.getX() + 0.5D, posVec31.getY() + 0.5D, posVec31.getZ() + 0.5D), new Vec3d(posVec32.getX() + 0.5D, posVec32.getY() + (double) EntityPixie.this.getHeight() * 0.5D, posVec32.getZ() + 0.5D), RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, EntityPixie.this)).getType() == RayTraceResult.Type.MISS;
        }

        public boolean shouldContinueExecuting() {
            return false;
        }

        public void tick() {
            if (!isDirectPathBetweenPoints(EntityPixie.this.getPosition(), target)) {
                target = EntityPixie.getPositionRelativetoGround(EntityPixie.this, EntityPixie.this.world, EntityPixie.this.getPosX() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.getPosZ() + EntityPixie.this.rand.nextInt(15) - 7, EntityPixie.this.rand);
            }
            if (EntityPixie.this.world.isAirBlock(target)) {
                EntityPixie.this.moveController.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                if (EntityPixie.this.getAttackTarget() == null) {
                    EntityPixie.this.getLookController().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                }
            }
        }
    }

    class AIEnterHouse extends Goal {
        public AIEnterHouse() {
            this.setMutexFlags(EnumSet.of(Flag.MOVE));
        }

        public boolean shouldExecute() {
            if (EntityPixie.this.isOwnerClose() || EntityPixie.this.getMoveHelper().isUpdating() || EntityPixie.this.isSitting() || EntityPixie.this.rand.nextInt(20) != 0 || EntityPixie.this.ticksUntilHouseAI != 0) {
                return false;
            }

            BlockPos blockpos1 = findAHouse(EntityPixie.this, EntityPixie.this.world);
            return !blockpos1.toString().equals(EntityPixie.this.getPosition().toString());
        }

        public boolean shouldContinueExecuting() {
            return false;
        }

        public void tick() {
            BlockPos blockpos = EntityPixie.this.getHousePos() == null ? EntityPixie.this.getPosition() : EntityPixie.this.getHousePos();

            if (blockpos == null) {
                blockpos = new BlockPos(EntityPixie.this);
            }

            for (int i = 0; i < 3; ++i) {
                BlockPos blockpos1 = findAHouse(EntityPixie.this, EntityPixie.this.world);
                EntityPixie.this.moveController.setMoveTo((double) blockpos1.getX() + 0.5D, (double) blockpos1.getY() + 0.5D, (double) blockpos1.getZ() + 0.5D, 0.25D);
                EntityPixie.this.housePos = blockpos1;
                if (EntityPixie.this.getAttackTarget() == null) {
                    EntityPixie.this.getLookController().setLookPosition((double) blockpos1.getX() + 0.5D, (double) blockpos1.getY() + 0.5D, (double) blockpos1.getZ() + 0.5D, 180.0F, 20.0F);
                }
            }
        }
    }
}