package extracells.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;
import appeng.api.IAEItemStack;
import appeng.api.Util;
import appeng.api.WorldCoord;
import appeng.api.events.GridTileLoadEvent;
import appeng.api.events.GridTileUnloadEvent;
import appeng.api.me.tiles.IDirectionalMETile;
import appeng.api.me.tiles.IGridMachine;
import appeng.api.me.tiles.ITileCable;
import appeng.api.me.util.IGridInterface;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;

public class TileEntityBusFluidImport extends TileEntity implements IGridMachine, IDirectionalMETile, IFluidHandler, ITileCable
{
	Boolean powerStatus = true, networkReady = true;	
	IGridInterface grid;
	ItemStack[] filterSlots = new ItemStack[8];
	private String costumName = StatCollector.translateToLocal("tile.block.fluid.bus.import");
	ECPrivateInventory inventory = new ECPrivateInventory(filterSlots, costumName, 1);
	Boolean redstoneAction = false;

	public TileEntityBusFluidImport()
	{
		powerStatus = false;
	}

	@Override
	public void updateEntity()
	{
		if (!worldObj.isRemote && isMachineActive())
		{
			ForgeDirection facing = ForgeDirection.getOrientation(getBlockMetadata());
			TileEntity facingTileEntity = worldObj.getBlockTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);

			if (grid != null && facingTileEntity != null && facingTileEntity instanceof IFluidHandler)
			{
				FluidTankInfo[] info = getTankInfo(facingTileEntity);
				if (info != null && info.length > 0)
				{
					FluidStack tankFluidStack = info[0].fluid;

					if (tankFluidStack != null && info[0].fluid.amount >= 20 && (isArrayEmpty(filterSlots) || arrayContains(filterSlots, new ItemStack(extracells.Extracells.FluidDisplay, 1, tankFluidStack.fluidID))))
					{
						IAEItemStack toImport = Util.createItemStack(new ItemStack(extracells.Extracells.FluidDisplay, 20, tankFluidStack.fluidID));
						FluidStack drainedStack = ((IFluidHandler) facingTileEntity).drain(facing, new FluidStack(tankFluidStack.getFluid(), 20), false);
						if (drainedStack != null)
						{
							toImport.setStackSize(drainedStack.amount);
							IAEItemStack notImported = grid.getCellArray().calculateItemAddition(toImport.copy());

							if (grid.useMEEnergy(12.0F, "Import Fluid") && notImported == null)
							{
								((IFluidHandler) facingTileEntity).drain(facing, (int) toImport.getStackSize(), true);
								grid.getCellArray().addItems(toImport.copy());
							}
						}
					}
				}
			}
		}
	}

	public boolean getRedstoneAction()
	{
		return redstoneAction;
	}

	public void toggleRedstoneAction(String playerName)
	{
		redstoneAction = !redstoneAction;
		updateGuiTile(playerName);
	}

	public void updateGuiTile(String playername)
	{
		Player player = (Player) worldObj.getPlayerEntityByName(playername);

		if (!worldObj.isRemote)
			PacketDispatcher.sendPacketToPlayer(getDescriptionPacket(), player);
	}

	@Override
	public Packet getDescriptionPacket()
	{
		NBTTagCompound nbtTag = new NBTTagCompound();
		this.writeToNBT(nbtTag);
		return new Packet132TileEntityData(this.xCoord, this.yCoord, this.zCoord, 1, nbtTag);
	}

	@Override
	public void onDataPacket(INetworkManager net, Packet132TileEntityData packet)
	{
		readFromNBT(packet.data);
	}

	public boolean isArrayEmpty(Object[] array)
	{
		for (Object cake : array)
		{
			if (cake != null)
				return false;
		}
		return true;
	}

	public FluidTankInfo[] getTankInfo(TileEntity tileEntity)
	{

		ForgeDirection facing = ForgeDirection.getOrientation(getBlockMetadata());
		FluidTankInfo[] tankArray;
		IFluidHandler tankTile = (IFluidHandler) tileEntity;

		if (((IFluidHandler) tileEntity).getTankInfo(facing) != null && ((IFluidHandler) tileEntity).getTankInfo(facing).length != 0)
		{
			return tankTile.getTankInfo(facing);
		} else if (((IFluidHandler) tileEntity).getTankInfo(facing) != null && tankTile.getTankInfo(ForgeDirection.UNKNOWN).length != 0)
		{
			return tankTile.getTankInfo(ForgeDirection.UNKNOWN);
		} else
		{
			return null;
		}
	}

	private Boolean arrayContains(ItemStack[] array, ItemStack itemstack)
	{

		for (ItemStack entry : array)
		{
			if (entry != null && entry.getItem() instanceof IFluidContainerItem && ((IFluidContainerItem) entry.getItem()).getFluid(entry) != null && itemstack.getItemDamage() == ((IFluidContainerItem) entry.getItem()).getFluid(entry).fluidID)
				return true;
			if (entry != null && itemstack.getItemDamage() == FluidContainerRegistry.getFluidForFilledItem(entry).fluidID)
				return true;
		}
		return false;
	}

	@Override
	public void validate()
	{
		super.validate();
		MinecraftForge.EVENT_BUS.post(new GridTileLoadEvent(this, worldObj, getLocation()));
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		MinecraftForge.EVENT_BUS.post(new GridTileUnloadEvent(this, worldObj, getLocation()));
	}

	@Override
	public WorldCoord getLocation()
	{
		return new WorldCoord(xCoord, yCoord, zCoord);
	}

	@Override
	public boolean isValid()
	{
		return true;
	}

	@Override
	public void setPowerStatus(boolean hasPower)
	{
		powerStatus = hasPower;
	}

	@Override
	public boolean isPowered()
	{
		return powerStatus;
	}

	@Override
	public IGridInterface getGrid()
	{
		return grid;
	}

	@Override
	public void setGrid(IGridInterface gi)
	{
		grid = gi;
	}

	@Override
	public World getWorld()
	{
		return worldObj;
	}

	@Override
	public boolean canConnect(ForgeDirection dir)
	{
		return dir.ordinal() != this.blockMetadata;
	}

	@Override
	public float getPowerDrainPerTick()
	{
		return 0;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		NBTTagList nbttaglist = new NBTTagList();

		for (int i = 0; i < this.filterSlots.length; ++i)
		{
			if (this.filterSlots[i] != null)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Slot", (byte) i);
				this.filterSlots[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}
		}
		nbt.setTag("Items", nbttaglist);
		if (getInventory().isInvNameLocalized())
		{
			nbt.setString("CustomName", this.costumName);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		NBTTagList nbttaglist = nbt.getTagList("Items");
		this.filterSlots = new ItemStack[getInventory().getSizeInventory()];
		if (nbt.hasKey("CustomName"))
		{
			this.costumName = nbt.getString("CustomName");
		}
		for (int i = 0; i < nbttaglist.tagCount(); ++i)
		{
			NBTTagCompound nbttagcompound1 = (NBTTagCompound) nbttaglist.tagAt(i);
			int j = nbttagcompound1.getByte("Slot") & 255;

			if (j >= 0 && j < this.filterSlots.length)
			{
				this.filterSlots[j] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
			}
		}
		inventory = new ECPrivateInventory(filterSlots, costumName, 1);
	}

	public ECPrivateInventory getInventory()
	{
		return inventory;
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if (resource != null && getGrid() != null && isMachineActive() && from.ordinal() == this.blockMetadata)
		{
			IAEItemStack added;

			int amount = resource.amount;
			int fluidID = resource.fluidID;

			if (doFill)
			{
				added = grid.getCellArray().addItems(Util.createItemStack(new ItemStack(extracells.Extracells.FluidDisplay, amount, fluidID)));
			} else
			{
				added = grid.getCellArray().calculateItemAddition(Util.createItemStack(new ItemStack(extracells.Extracells.FluidDisplay, amount, fluidID)));
			}
			if (added == null)
			{
				if (doFill)
					grid.useMEEnergy(amount / 20, "Import Fluid");
				return resource.amount;
			} else
			{
				if (doFill)
					grid.useMEEnergy(amount - added.getStackSize() / 20, "Import Fluid");
				return (int) (resource.amount - added.getStackSize());
			}
		}
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return grid.getCellArray().canAccept(Util.createItemStack(new ItemStack(extracells.Extracells.FluidDisplay, 1, fluid.getID())));
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		if (getGrid() != null && from.ordinal() == this.blockMetadata)
		{
			List<FluidTankInfo> tankInfo = new ArrayList<FluidTankInfo>();
			FluidTankInfo[] tankArray = new FluidTankInfo[1];

			for (IAEItemStack item : getGrid().getCellArray().getAvailableItems())
			{
				if (item.getItem() == extracells.Extracells.FluidDisplay)
					tankInfo.add(new FluidTankInfo(new FluidStack(FluidRegistry.getFluid(item.getItemDamage()), (int) item.getStackSize()), (int) getGrid().getCellArray().freeBytes()));
			}

			if (tankInfo.isEmpty())
				tankInfo.add(new FluidTankInfo(null, (int) getGrid().getCellArray().freeBytes()));

			tankArray = tankInfo.toArray(tankArray);
			return tankArray;
		}
		return null;
	}

	@Override
	public boolean coveredConnections()
	{
		return false;
	}

	@Override
	public void setNetworkReady(boolean isReady)
	{
		networkReady = isReady;
	}

	@Override
	public boolean isMachineActive()
	{
		return powerStatus && networkReady;
	}
}
