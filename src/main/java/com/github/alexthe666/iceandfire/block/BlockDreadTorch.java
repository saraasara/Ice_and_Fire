package com.github.alexthe666.iceandfire.block;

import com.github.alexthe666.iceandfire.IceAndFire;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

public class BlockDreadTorch extends TorchBlock implements IDreadBlock, IWallBlock {

    public BlockDreadTorch() {
        super(Properties.create(Material.WOOD).lightValue(0).sound(SoundType.STONE).lightValue(7).notSolid().variableOpacity());
        setRegistryName(IceAndFire.MODID, "dread_torch");
    }

    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        // Direction Direction = stateIn.get(FACING);
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getY() + 0.6D;
        double d2 = (double) pos.getZ() + 0.5D;
        double d3 = 0.22D;
        double d4 = 0.27D;
        IceAndFire.PROXY.spawnParticle("dread_torch", d0, d1, d2, 0.0D, 0.0D, 0.0D);
  /*   if (Direction.getAxis().isHorizontal()) {
            Direction Direction1 = Direction.getOpposite();
            //worldIn.spawnParticle(ParticleTypes.SMOKE_NORMAL, d0 + 0.27D * (double)Direction1.getXOffset(), d1 + 0.22D, d2 + 0.27D * (double)Direction1.getZOffset(), 0.0D, 0.0D, 0.0D);
            IceAndFire.PROXY.spawnParticle("dread_torch", d0 + 0.27D * (double) Direction1.getXOffset(), d1 + 0.22D, d2 + 0.27D * (double) Direction1.getZOffset(), 0.0D, 0.0D, 0.0D);

        } else {
            //worldIn.spawnParticle(ParticleTypes.SMOKE_NORMAL, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }*/
    }

    @Override
    public Block wallBlock() {
        return IafBlockRegistry.DREAD_TORCH_WALL;
    }
}