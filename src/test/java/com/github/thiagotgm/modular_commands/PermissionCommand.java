/*
 * This file is part of ModularCommands.
 *
 * ModularCommands is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ModularCommands is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ModularCommands. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.thiagotgm.modular_commands;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.NavigableSet;
import java.util.TreeSet;

import com.github.thiagotgm.modular_commands.api.CommandContext;
import com.github.thiagotgm.modular_commands.api.CommandRegistry;
import com.github.thiagotgm.modular_commands.api.FailureReason;
import com.github.thiagotgm.modular_commands.api.ICommand;

import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

/**
 * Test command that needs some guild and channel permissions.
 *
 * @version 1.0
 * @author ThiagoTGM
 * @since 2017-07-16
 */
public class PermissionCommand implements ICommand {

    private CommandRegistry registry;
    private volatile boolean enabled;

    public PermissionCommand() {
        
        this.enabled = true;
        
    }

    @Override
    public boolean isEnabled() {

        return enabled;
        
    }

    @Override
    public void setEnabled( boolean enabled ) throws IllegalStateException {

        this.enabled = enabled;

    }

    @Override
    public CommandRegistry getRegistry() {

        return registry;
        
    }

    @Override
    public void setRegistry( CommandRegistry registry ) {

        this.registry = registry;

    }

    @Override
    public String getName() {

        return "Permissions required";
        
    }

    @Override
    public NavigableSet<String> getAliases() {

        String[] alias = { "permission" };
        return new TreeSet<>( Arrays.asList( alias ) );
        
    }

    @Override
    public boolean isSubCommand() {

        return false;
        
    }
    
    @Override
    public String getPrefix() { return "per!"; }

    @Override
    public boolean execute( CommandContext context )
            throws RateLimitException, MissingPermissionsException, DiscordException {

        context.getReplyBuilder().withContent( "has permissions!" ).build();
        return true;

    }
    
    @Override
    public EnumSet<Permissions> getRequiredPermissions() {
        
        return EnumSet.of( Permissions.ATTACH_FILES );
        
    }
    
    @Override
    public EnumSet<Permissions> getRequiredGuildPermissions() {
        
        return EnumSet.of( Permissions.EMBED_LINKS );
        
    }
    
    @Override
    public void onFailure( CommandContext context, FailureReason reason )
            throws RateLimitException, MissingPermissionsException, DiscordException {
        
        if ( reason == FailureReason.USER_MISSING_PERMISSIONS ) {
            context.getReplyBuilder().withContent( "missing permissions!" ).build();
        }
        else if ( reason == FailureReason.USER_MISSING_GUILD_PERMISSIONS ) {
            context.getReplyBuilder().withContent( "missing server permissions!" ).build();
        } else {
            context.getReplyBuilder().withContent( "other failure!" ).build();
        }
        
    }

}
