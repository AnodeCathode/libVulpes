package zmaster587.libVulpes.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandler.Sharable;

import java.util.EnumMap;

import zmaster587.libVulpes.network.BasePacket.BasePacketHandlerClient;
import zmaster587.libVulpes.network.BasePacket.BasePacketHandlerServer;

import com.google.common.collect.Maps;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.FMLIndexedMessageToMessageCodec;
import net.minecraftforge.fml.common.network.FMLOutboundHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketHandler {
	public static final EnumMap<Side, FMLEmbeddedChannel> channels = Maps.newEnumMap(Side.class);
	
	public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("mymodid");

	private static int discriminatorNumber = 0;
	static Codec codec = new Codec();
	
	public static void init() {
		if (!channels.isEmpty()) // avoid duplicate inits..
			return;
		
		HandlerServer handle = new HandlerServer();
		
		channels.putAll(NetworkRegistry.INSTANCE.newChannel("libVulpes", handle));
		
		
		// add handlers
		if (FMLCommonHandler.instance().getSide().isClient())
		{
			// for the client
			FMLEmbeddedChannel channel = channels.get(Side.CLIENT);
			
			String codecName = channel.findChannelHandlerNameForType(handle.getClass());
			channel.pipeline().addAfter(codecName, "ClientHandler", new HandlerClient());
		}
	}
	
	public static final void addDiscriminator(Class clazz) {
		if(FMLCommonHandler.instance().getSide().isClient())
			INSTANCE.registerMessage(BasePacketHandlerClient.class, clazz, discriminatorNumber++, Side.CLIENT);
		INSTANCE.registerMessage(BasePacketHandlerServer.class, clazz, discriminatorNumber++, Side.SERVER);
		
		/*if(codec != null) {
			codec.addDiscriminator(discriminatorNumber, clazz);
			discriminatorNumber++;
		}
		else
			LibVulpes.logger.warn("Trying to register " + clazz.getName() + " after preinit!!");*/
	}


	public static final void sendToServer(BasePacket packet) {
		INSTANCE.sendToServer(packet);
		//channels.get(Side.CLIENT).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.TOSERVER);
		//channels.get(Side.CLIENT).writeOutbound(packet);
	}

	
	public static final void sendToPlayersTrackingEntity(BasePacket packet, Entity entity) {
		for( EntityPlayer player : ((WorldServer)entity.worldObj).getEntityTracker().getTrackingPlayers(entity)) {

			INSTANCE.sendTo(packet, (EntityPlayerMP)player);
			//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
			//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
			//channels.get(Side.SERVER).writeOutbound(packet);
		}
	}

	public static final void sendToAll(BasePacket packet) {
		INSTANCE.sendToAll(packet);
		//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
		//channels.get(Side.SERVER).writeOutbound(packet);
	}
	
	public static final void sendToPlayer(BasePacket packet, EntityPlayer player) {
		INSTANCE.sendTo(packet, (EntityPlayerMP)player);
		//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
		//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
		//channels.get(Side.SERVER).writeOutbound(packet);
	}

	public static final void sendToDispatcher(BasePacket packet, NetworkManager netman) {
		channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.DISPATCHER);
		channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(NetworkDispatcher.get(netman));
		channels.get(Side.SERVER).writeOutbound(packet);
	}
	
	public static final void sendToNearby(BasePacket packet,int dimId, int x, int y, int z, double dist) {
		INSTANCE.sendToAllAround(packet, new TargetPoint(dimId, x, y, z, dist));
		//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALLAROUNDPOINT);
		//channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(new NetworkRegistry.TargetPoint(dimId, x, y, z,dist));
		//channels.get(Side.SERVER).writeOutbound(packet);
	}

	public static final void sendToNearby(BasePacket packet,int dimId, BlockPos pos, double dist) {
		sendToNearby(packet, dimId, pos.getX(), pos.getY(), pos.getZ(), dist);
	}
	
	private static final class Codec extends FMLIndexedMessageToMessageCodec<BasePacket> {

		@Override
		public void encodeInto(ChannelHandlerContext ctx, BasePacket msg,
				ByteBuf data) throws Exception {
			msg.write(data);
		}

		@Override
		public void decodeInto(ChannelHandlerContext ctx, ByteBuf data, BasePacket packet) {


			switch (FMLCommonHandler.instance().getEffectiveSide()) {
			case CLIENT:
				packet.readClient(data);
				//packet.executeClient((EntityPlayer)Minecraft.getMinecraft().thePlayer);
				break;
			case SERVER:
				INetHandler netHandler = ctx.channel().attr(NetworkRegistry.NET_HANDLER).get();
				packet.read(data);
				//packet.executeServer(((NetHandlerPlayServer) netHandler).playerEntity);
				break;
			}

		}
	}

	@Sharable
	@SideOnly(Side.CLIENT)
	private static final class HandlerClient extends SimpleChannelInboundHandler<BasePacket>
	{
		@Override
		protected void channelRead0(ChannelHandlerContext ctx, BasePacket packet) throws Exception
		{
			Minecraft mc = Minecraft.getMinecraft();
			packet.executeClient(mc.thePlayer); //actionClient(mc.theWorld, );
		}
	}
	@Sharable
	private static final class HandlerServer extends SimpleChannelInboundHandler<BasePacket>
	{
		@Override
		protected void channelRead0(ChannelHandlerContext ctx, BasePacket packet) throws Exception
		{
			if (FMLCommonHandler.instance().getEffectiveSide().isClient())
			{
				// nothing on the client thread
				return;
			}
			EntityPlayerMP player = ((NetHandlerPlayServer) ctx.channel().attr(NetworkRegistry.NET_HANDLER).get()).playerEntity;
			packet.executeServer(player); //(player.worldObj, player);
		}
	}

}
