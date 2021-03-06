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

package com.github.thiagotgm.modular_commands.annotation;

import com.github.thiagotgm.modular_commands.api.CommandContext;
import com.github.thiagotgm.modular_commands.command.annotation.MainCommand;

/**
 * Annotation-based commands.
 *
 * @version 1.0
 * @author ThiagoTGM
 * @since 2017-07-21
 */
public class SubAnnotatedCommands {

    @MainCommand(
            name = "Annotated command of a submodule",
            aliases = { "submodule" }
            )
    public void command( CommandContext context ) {
        
        context.getReplyBuilder().withContent( "Submodule!" ).build();
        
    }

}
