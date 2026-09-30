package com.bettercontent.betterworldmanagement.api.event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;
public final class SchematicSubstitutionEvent extends Event {
 public final ServerLevel level;public final UUID owner,operation;public final BlockPos position;public final String source,target;
 public SchematicSubstitutionEvent(ServerLevel level,UUID owner,UUID operation,BlockPos position,String source,String target){this.level=level;this.owner=owner;this.operation=operation;this.position=position.immutable();this.source=source;this.target=target;}
}
