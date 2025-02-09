/*
 * ioGame
 * Copyright (C) 2021 - present  渔民小镇 （262610965@qq.com、luoyizhu@gmail.com） . All Rights Reserved.
 * # iohao.com . 渔民小镇
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.iohao.game.external.client.join;

import com.iohao.game.common.consts.IoGameLogName;
import com.iohao.game.external.client.ClientConnectOption;
import com.iohao.game.external.client.join.handler.ClientMessageHandler;
import com.iohao.game.external.client.user.ClientUser;
import com.iohao.game.external.client.user.ClientUserChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;

/**
 * @author 渔民小镇
 * @date 2023-07-05
 */
@Slf4j(topic = IoGameLogName.CommonStdout)
class TcpClientStartup implements ClientConnect {
    static int PACKAGE_MAX_SIZE = 1024 * 1024;

    @Override
    public void connect(ClientConnectOption option) {
        ClientUser clientUser = option.getClientUser();
        ClientMessageHandler clientMessageHandler = new ClientMessageHandler(clientUser);

        EventLoopGroup group = new NioEventLoopGroup();
        var bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 编排网关业务
                        ChannelPipeline pipeline = ch.pipeline();

                        // 数据包长度 = 长度域的值 + lengthFieldOffset + lengthFieldLength + lengthAdjustment。
                        pipeline.addLast(new ChannelHandler[]{new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, TcpClientStartup.PACKAGE_MAX_SIZE, 0, 4, 0, 0, true)});

                        // 编解码
                        pipeline.addLast("codec", new ClientTcpExternalCodec());

                        pipeline.addLast(clientMessageHandler);
                    }
                });

        InetSocketAddress address = option.getSocketAddress();
        String hostName = address.getHostName();
        int port = address.getPort();
        final ChannelFuture channelFuture = bootstrap.connect(hostName, port);

        try {
            Channel channel = channelFuture.sync().channel();

            ClientUserChannel userChannel = clientUser.getClientUserChannel();
            userChannel.setClientChannel(channel::writeAndFlush);

            userChannel.setCloseChannel(channel::close);

            clientUser.getClientUserInputCommands().start();

            channel.closeFuture().await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
