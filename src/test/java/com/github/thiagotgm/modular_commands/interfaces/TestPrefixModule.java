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

package com.github.thiagotgm.modular_commands.interfaces;

import com.github.thiagotgm.modular_commands.api.CommandRegistry;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.modules.IModule;

/**
 * Module for registry prefix testing.
 *
 * @version 1.0
 * @author ThiagoTGM
 * @since 2017-07-16
 */
public class TestPrefixModule implements IModule {

    @Override
    public boolean enable( IDiscordClient client ) {

        CommandRegistry reg = CommandRegistry.getRegistry( client ).getSubRegistry( this );
        reg.registerCommand( new LowPriorityCommand() );
        reg.registerCommand( new HighPriorityCommand() );
        reg.setPrefix( "pre||" );
        return true;
        
    }

    @Override
    public void disable() {

        // TODO Auto-generated method stub

    }

    @Override
    public String getName() {

        return "Prefixed Module";
    }

    @Override
    public String getAuthor() {

        return "ThiagoTGM";
    }

    @Override
    public String getVersion() {

        return "1.0.0";
    }

    @Override
    public String getMinimumDiscord4JVersion() {

        return "2.8.4";
    }

}
