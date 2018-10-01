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

package com.github.thiagotgm.modular_commands.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.thiagotgm.modular_commands.command.annotation.AnnotationParser;
import com.github.thiagotgm.modular_commands.registry.ClientCommandRegistry;
import com.github.thiagotgm.modular_commands.registry.PlaceholderCommandRegistry;
import com.github.thiagotgm.modular_commands.registry.annotation.Essential;
import com.github.thiagotgm.modular_commands.registry.annotation.HasPrefix;
import com.github.thiagotgm.modular_commands.registry.annotation.Named;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.modules.IModule;

/**
 * A registry that allows registering of commands to be later called, or other
 * registries as subregistries.
 * <p>
 * A registry can only be registered to one parent registry.
 * <p>
 * This class is <i>thread-safe</i>.
 *
 * @version 1.1
 * @author ThiagoTGM
 * @since 2017-07-12
 */
public class CommandRegistry implements Disableable, Prefixed, Comparable<CommandRegistry> {

    /**
     * Default prefix to be inherited when no prefix was specified in the
     * inheritance chain.
     */
    public static final String DEFAULT_PREFIX = "?";

    /**
     * Separator used between the names of each registry in the {@link #getPath()
     * path}.
     */
    public static final char PATH_SEPARATOR = '/';

    private static final Predicate<CommandContext> NO_CHECK = c -> true;

    private static final Logger LOG = LoggerFactory.getLogger( CommandRegistry.class );

    /** Map of the root registries for each client. */
    private static final Map<IDiscordClient,
            ClientCommandRegistry> registries = Collections.synchronizedMap( new HashMap<>() );

    /**
     * Map between classes that can be linked to a registry and the registry subtype
     * that links to objects of that class.
     * 
     * @deprecated Class-specific registries are not used anymore.
     */
    @Deprecated
    protected static final Map<Class<?>, Class<? extends CommandRegistry>> registryTypes = new HashMap<>();
    /**
     * Map between classes that can be linked to a registry and the qualifier of the
     * registry subtype that links to objects of that class.
     * 
     * @deprecated Qualified names aren't used anymore.
     */
    @Deprecated
    protected static final Map<Class<?>, String> qualifiers = new HashMap<>();

    /**
     * Retrieves the registry linked to a given client.
     * <p>
     * If no such registry is registered, creates one.
     *
     * @param client
     *            The client whose linked registry should be retrieved.
     * @return The registry linked to the given client.
     * @throws NullPointerException
     *             if the client passed in is null.
     */
    public static ClientCommandRegistry getRegistry( IDiscordClient client ) throws NullPointerException {

        if ( client == null ) {
            throw new NullPointerException( "Client argument cannot be null." );
        }

        ClientCommandRegistry registry;
        synchronized ( registries ) {

            registry = registries.get( client );
            if ( registry == null ) { // Registry not found, create one.
                registry = new ClientCommandRegistry( client );
                registries.put( client, registry );
            }

        }
        return registry;

    }

    /**
     * Determines if there is a registry that is linked to the given client.
     *
     * @param client
     *            The client to check for.
     * @return true if there is a registry linked to the given client. false
     *         otherwise.
     * @throws NullPointerException
     *             if the client passed in is null.
     */
    public static boolean hasRegistry( IDiscordClient client ) throws NullPointerException {

        if ( client == null ) {
            throw new NullPointerException( "Client argument cannot be null." );
        }
        return registries.containsKey( client );

    }

    /**
     * Removes the registry that is linked to the given client, if there is one.
     * <p>
     * After removing the registry, the next to call to
     * {@link #getRegistry(IDiscordClient)} using the given client will create a new
     * registry.
     *
     * @param client
     *            The client whose linked registry is to be removed.
     * @return The (removed) registry that is linked to the given client, or null if
     *         there was no such registry.
     * @throws NullPointerException
     *             if the client passed in is null.
     */
    public static ClientCommandRegistry removeRegistry( IDiscordClient client ) throws NullPointerException {

        if ( client == null ) {
            throw new NullPointerException( "Client argument cannot be null." );
        }
        return registries.remove( client );

    }

    /**
     * Given the qualifier of a registry type and the name of a registry, retrieves
     * the qualified name.
     *
     * @param qualifier
     *            Qualifier of the registry type.
     * @param name
     *            Name of the registry.
     * @return The qualified registry name.
     * 
     * @deprecated Qualified names are no longer used.
     */
    @Deprecated
    protected static String qualifiedName( String qualifier, String name ) {

        return name;

    }

    /**
     * Given a class and a name, retrieves the qualified name of the registry that
     * has the given name and is associated to an object of the given type.
     *
     * @param linkedClass
     *            The class of objects that the registry type links to.
     * @param name
     *            The name of the registry.
     * @return The qualified name of the registry.
     * 
     * @deprecated Qualified names are no longer used.
     */
    @Deprecated
    protected static String qualifiedName( Class<?> linkedClass, String name ) {

        return qualifiedName( "", name );

    }

    /**
     * Determines the name used for a registry made after the given class.
     * <p>
     * If the class has a {@link Named} annotation, uses the name specified there.
     * Else, uses the (simple) name of the class.
     *
     * @param clazz
     *            The class to get a registry name from.
     * @return The registry name to use.
     * @throws NullPointerException
     *             if the given class is <tt>null</tt>.
     */
    public static String getNameFromClass( Class<?> clazz ) throws NullPointerException {

        Named nameAnnotation = clazz.getAnnotation( Named.class );
        if ( nameAnnotation != null ) {
            return nameAnnotation.value();
        } else {
            return clazz.getSimpleName();
        }

    }

    private final String name;
    private volatile boolean enabled;
    private volatile String prefix;
    private volatile Predicate<CommandContext> contextCheck;
    private final List<Predicate<CommandContext>> contextChecks;
    private volatile long lastChanged;
    /** Stores whether the registry is essential. */
    protected volatile boolean essential;

    /** Table of commands, stored by name. */
    private final Map<String, ICommand> commands;
    /** Table of commands with specified prefix, stored by (each) signature. */
    private final Map<String, PriorityQueue<ICommand>> withPrefix;
    /** Table of commands with no specified prefix, stored by (each) signature. */
    private final Map<String, PriorityQueue<ICommand>> noPrefix;

    private volatile CommandRegistry parentRegistry;
    /** Map of the subregistries registered in this registry and their names. */
    protected final Map<String, CommandRegistry> subRegistries;
    /** Map of the placeholders stored in this registry and their names. */
    protected final Map<String, PlaceholderCommandRegistry> placeholders;

    /**
     * Initializes a registry with the given name.
     *
     * @param name
     *            The name of the registry.
     * @param essential
     *            Whether the registry is essential.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the given name contains the {@link #PATH_SEPARATOR}.
     */
    protected CommandRegistry( String name, boolean essential ) throws NullPointerException, IllegalArgumentException {

        if ( name.contains( Character.toString( PATH_SEPARATOR ) ) ) {
            throw new IllegalArgumentException( "Registry name cannot contain '" + PATH_SEPARATOR + "'." );
        }
        this.name = name;
        this.essential = essential;

        // Initializes defaults.

        this.enabled = true;
        this.prefix = null;
        this.contextCheck = NO_CHECK;
        this.contextChecks = new LinkedList<>();
        this.lastChanged = System.currentTimeMillis();

        this.commands = new ConcurrentHashMap<>();
        this.withPrefix = new ConcurrentHashMap<>();
        this.noPrefix = new ConcurrentHashMap<>();

        this.subRegistries = new ConcurrentHashMap<>();
        this.placeholders = new ConcurrentHashMap<>();

    }

    /**
     * Initializes a registry named after the given class, as given by
     * {@link #getNameFromClass(Class)}.
     * <p>
     * If the class has the {@link Essential} annotation, the registry is
     * initialized as essential.<br>
     * If the class has the {@link HasPrefix} annotation, the registry is
     * initialized to have the prefix given by that annotation.
     *
     * @param clazz
     *            The class to name the registry after.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the name specified by the given class contains the
     *             {@link #PATH_SEPARATOR}.
     */
    protected CommandRegistry( Class<?> clazz ) {

        this( getNameFromClass( clazz ), clazz.isAnnotationPresent( Essential.class ) );

        HasPrefix prefixAnnotation = clazz.getAnnotation( HasPrefix.class );
        if ( prefixAnnotation != null ) {
            this.prefix = prefixAnnotation.value();
        }

    }

    /**
     * Creates a new registry with no declared prefix and linked to no object.
     * 
     * @deprecated Use {@link #CommandRegistry(String, boolean)} instead.
     */
    @Deprecated
    protected CommandRegistry() {

        this( "", false );

    }

    /**
     * Retrieves the name of the registry.
     *
     * @return The name that identifies the registry.
     */
    public String getName() {

        return name;

    }

    /**
     * Retrieves the <i>path</i> of this registry, that is, the name of this
     * registry prepended by the name of each parent registry in order, separated by
     * the {@link #PATH_SEPARATOR}.
     *
     * @return The path of this registry.
     */
    public String getPath() {

        LinkedList<String> path = new LinkedList<>();
        CommandRegistry curr = this;
        while ( curr != null ) {

            path.addFirst( curr.getName() );
            curr = curr.getRegistry();

        }
        return path.size() > 1 ? String.join( Character.toString( PATH_SEPARATOR ), path )
                : Character.toString( PATH_SEPARATOR );

    }

    /**
     * Retrieves the qualifier that identifies the registry type.
     *
     * @return The qualifier of caller's registry type.
     * 
     * @deprecated Qualified names are no longer used.
     */
    @Deprecated
    public String getQualifier() {

        return "";

    }

    /**
     * Retrieves the fully qualified name of the registry.
     *
     * @return The qualified name.
     * 
     * @deprecated Qualified names are no longer used.
     */
    @Deprecated
    public String getQualifiedName() {

        return getName();

    }

    /**
     * Retrieves the object that the registry is linked to.
     * <p>
     * Will return null if this is a placeholder (placeholders are not linked to any
     * object).
     *
     * @return The linked object.
     * 
     * @deprecated Registries are now not linked to any object. Will always return
     *             <tt>null</tt>.
     */
    @Deprecated
    public Object getLinkedObject() {

        return null;

    }

    /**
     * Transfers all subregistries (including placeholders) in the given registry to
     * the calling registry.
     *
     * @param registry
     *            The registry to get subregistries from.
     */
    protected void transferSubRegistries( CommandRegistry registry ) {

        synchronized ( CommandRegistry.class ) {

            LOG.trace( "Transferring subregistries and placeholders from \"{}\" to \"{}\".", registry.getPath(),
                    getPath() );

            /* Transfer subregistries */
            Iterator<CommandRegistry> subRegistryIter = registry.subRegistries.values().iterator();
            while ( subRegistryIter.hasNext() ) {

                CommandRegistry subRegistry = subRegistryIter.next();
                subRegistryIter.remove(); // Remove from previous registry.
                subRegistry.setRegistry( this ); // Add to this registry.
                this.subRegistries.put( subRegistry.getName(), subRegistry );

            }

            /* Transfer placeholders */
            Iterator<PlaceholderCommandRegistry> placeholderIter = registry.placeholders.values().iterator();
            while ( placeholderIter.hasNext() ) {

                PlaceholderCommandRegistry placeholder = placeholderIter.next();
                placeholderIter.remove(); // Remove from previous registry.
                placeholder.setRegistry( this ); // Add to this registry.
                this.placeholders.put( placeholder.getName(), placeholder );

            }
            setLastChanged( System.currentTimeMillis() );
            registry.setLastChanged( System.currentTimeMillis() );

        }

    }

    /**
     * Registers a new subregistry into this registry.
     * <p>
     * If the given registry was already registered into another registry, it is
     * unregistered from it.
     * <p>
     * If the registry is already registered as a subregistry, does nothing.
     *
     * @param registry
     *            The subregistry to register.
     * @throws IllegalArgumentException
     *             if the registry given is the calling registry (attempted to
     *             register a registry into itself).
     */
    protected void registerSubRegistry( CommandRegistry registry ) throws IllegalArgumentException {

        if ( registry == this ) {
            throw new IllegalArgumentException( "Attempted to register a registry into itself." );
        }

        synchronized ( subRegistries ) {

            String name = registry.getName();
            if ( subRegistries.containsKey( name ) ) {
                return; // If already registered, do nothing.
            }
            subRegistries.put( name, registry );

        }

        synchronized ( registry ) {

            if ( registry.getRegistry() != null ) { // Unregister from previous registry if any.
                registry.getRegistry().unregisterSubRegistry( registry );
            }
            registry.setRegistry( this );

        }

        LOG.info( "Adding subregistry \"{}\" to \"{}\".", name, getPath() );
        setLastChanged( System.currentTimeMillis() );

    }

    /**
     * Unregisters the given registry from this registry. If the given registry was
     * not a subregistry of the calling registry, nothing is changed.
     *
     * @param registry
     *            The subregistry to be unregistered.
     */
    protected void unregisterSubRegistry( CommandRegistry registry ) {

        synchronized ( subRegistries ) {

            if ( subRegistries.remove( registry.getName() ) != null ) {
                registry.setRegistry( null );
                LOG.info( "Removing subregistry \"{}\" from \"{}\".", registry.getName(), getPath() );
            }

        }
        setLastChanged( System.currentTimeMillis() );

    }

    /**
     * Retrieves the subregistry that has the given name. If one does not exist,
     * creates one using the given supplier, retrieving data from the placeholder if
     * there is one.
     *
     * @param name
     *            The name of the subregistry.
     * @return The subregistry with the given name.
     * @throws IllegalArgumentException
     *             if the given name contains the {@link #PATH_SEPARATOR}.
     */
    private CommandRegistry getSubRegistry( String name, Supplier<CommandRegistry> registryMaker )
            throws NullPointerException, IllegalArgumentException {

        synchronized ( subRegistries ) {

            CommandRegistry registry = subRegistries.get( name );
            if ( registry == null ) { // No subregistry yet.
                registry = registryMaker.get();
                LOG.info( "Creating subregistry \"{}\" in \"{}\".", name, getPath() );

                PlaceholderCommandRegistry placeholder = placeholders.remove( name );
                if ( placeholder != null ) { // Registry has a placeholder.
                    LOG.debug( "Absorbing placeholder." );
                    registry.transferSubRegistries( placeholder );
                }
                registerSubRegistry( registry );
            }
            return registry;

        }

    }

    /**
     * Retrieves the subregistry that has the given name. If one does not exist,
     * creates one, retrieving data from the placeholder if there is one.
     *
     * @param name
     *            The name of the subregistry.
     * @return The subregistry with the given name.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the given name contains the {@link #PATH_SEPARATOR}.
     */
    public CommandRegistry getSubRegistry( String name ) throws NullPointerException, IllegalArgumentException {

        if ( name == null ) {
            throw new NullPointerException( "Subregistry name cannot be null." );
        }

        return getSubRegistry( name, () -> new CommandRegistry( name, false ) );

    }

    /**
     * Retrieves the subregistry that has the given name. If one does not exist,
     * returns a placeholder registry that can be used to obtain further
     * subregistries, but cannot register any commands.
     *
     * @param name
     *            The name of the subregistry.
     * @return The subregistry with the given name, which may be a placeholder that
     *         cannot register commands.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the given name contains the {@link #PATH_SEPARATOR}.
     */
    public CommandRegistry getSubRegistryOrPlaceholder( String name )
            throws NullPointerException, IllegalArgumentException {

        if ( name == null ) {
            throw new NullPointerException( "Subregistry name cannot be null." );
        }

        synchronized ( subRegistries ) {

            CommandRegistry registry = subRegistries.get( name );
            if ( registry != null ) {
                return registry; // Has an actual registry.
            }

            synchronized ( placeholders ) {

                registry = placeholders.get( name );
                if ( registry != null ) {
                    return registry; // Has a placeholder.
                }

                // Create a new placeholder.
                PlaceholderCommandRegistry placeholder = new PlaceholderCommandRegistry( name );
                LOG.info( "Creating placeholder for \"{}\" in \"{}\".", name, getPath() );
                placeholders.put( name, placeholder );
                placeholder.setRegistry( this );
                return placeholder;

            }

        }

    }

    /**
     * Retrieves the subregistry named after the given class (as specified by
     * {@link #getNameFromClass(Class)}). If one does not exist, creates one,
     * retrieving data from the placeholder if there is one.
     * <p>
     * If the subregistry is created:<br>
     * If the class has the {@link Essential} annotation, the registry is
     * initialized as essential.<br>
     * If the class has the {@link HasPrefix} annotation, the registry is
     * initialized to have the prefix given by that annotation.
     *
     * @param clazz
     *            The class that the subregistry should be named after.
     * @return The subregistry named after the given class.
     * @throws NullPointerException
     *             if the given class is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the name to use for the given class contains the
     *             {@link #PATH_SEPARATOR}.
     */
    public CommandRegistry getSubRegistry( Class<?> clazz ) throws NullPointerException, IllegalArgumentException {

        return getSubRegistry( getNameFromClass( clazz ), () -> new CommandRegistry( clazz ) );

    }

    /**
     * Retrieves the subregistry named after the given class (as specified by
     * {@link #getNameFromClass(Class)}). If one does not exist, returns a
     * placeholder registry that can be used to obtain further subregistries, but
     * cannot register any commands.
     *
     * @param clazz
     *            The class that the subregistry should be named after.
     * @return The subregistry named after the given class, which may be a
     *         placeholder that cannot register commands.
     * @throws NullPointerException
     *             if the given class is <tt>null</tt>.
     * @throws IllegalArgumentException
     *             if the name to use for the given class contains the
     *             {@link #PATH_SEPARATOR}.
     */
    public CommandRegistry getSubRegistryOrPlaceholder( Class<?> clazz )
            throws NullPointerException, IllegalArgumentException {

        return getSubRegistryOrPlaceholder( getNameFromClass( clazz ) );

    }

    /**
     * Retrieves the subregistry that is associated to an object of the given class
     * and has the given name.
     * <p>
     * If no such registry exists, but there is a placeholder for it, retrieves the
     * placeholder.<br>
     * Else, creates a placeholder and retrieves it.
     *
     * @param linkedClass
     *            The class of objects that the desired registry links to.
     * @param name
     *            The simple name of the subregistry.
     * @return The subregistry with the qualified name (actual or placeholder).
     * @throws NullPointerException
     *             if one of the arguments is null.
     * @throws IllegalArgumentException
     *             if there is no registry for the given type.
     * 
     * @deprecated Use {@link #getSubRegistryOrPlaceholder(Class)} instead.
     */
    @Deprecated
    public CommandRegistry getSubRegistry( Class<?> linkedClass, String name )
            throws NullPointerException, IllegalArgumentException {

        return getSubRegistryOrPlaceholder( linkedClass );

    }

    /**
     * Determines if there is a subregistry registered in this registry that has the
     * given name.
     *
     * @param name
     *            The name to check for.
     * @return <tt>true</tt> if there is a subregistry with the given name.
     *         <tt>false</tt> otherwise.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     */
    public boolean hasSubRegistry( String name ) throws NullPointerException {

        if ( name == null ) {
            throw new NullPointerException( "Name cannot be null." );
        }
        return subRegistries.containsKey( name );

    }

    /**
     * Determines if there is a subregistry registered in this registry that has is
     * named after the given class (as given by {@link #getNameFromClass(Class)}).
     *
     * @param clazz
     *            The class to check for.
     * @return <tt>true</tt> if there is a subregistry named after the given class.
     *         <tt>false</tt> otherwise.
     * @throws NullPointerException
     *             if the given class is <tt>null</tt>.
     */
    public boolean hasSubRegistry( Class<?> clazz ) throws NullPointerException {

        return subRegistries.containsKey( getNameFromClass( clazz ) );

    }

    /**
     * Deletes all empty placeholder registries starting from the calling registry
     * and going up the registry hierarchy, until hitting either a non-placeholder
     * registry or a non-empty placeholder.
     */
    private void cleanPlaceholders() {

        CommandRegistry registry = this;
        while ( ( registry instanceof PlaceholderCommandRegistry ) && ( registry.subRegistries.isEmpty() )
                && ( registry.placeholders.isEmpty() ) ) {

            CommandRegistry parent = registry.getRegistry();
            LOG.debug( "Removing empty placeholder \"{}\".", registry.getPath() );
            parent.placeholders.remove( registry.getName() );
            registry = parent;

        }

    }

    /**
     * Removes the subregistry with the given name, if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, leaves a
     * placeholder for it that keeps the subregistries, so that if the subregistry
     * is recreated, the subregistries (and placeholders) are brought back.<br>
     * Thus, if a registry is found and deleted, it will always be returned with no
     * subregistries or placeholders, as any it might have had are left with the
     * placeholder.<br>
     * If the subregistries should be also deleted/kept with the registry, use
     * {@link #removeSubRegistryFull(String)}.
     *
     * @param name
     *            The name of the subregistry.
     * @return The deleted subregistry, or <tt>null</tt> if no matching subregistry
     *         found.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     */
    public CommandRegistry removeSubRegistry( String name ) throws NullPointerException {

        if ( name == null ) {
            throw new NullPointerException( "Name cannot be null." );
        }

        synchronized ( subRegistries ) {

            CommandRegistry subRegistry = subRegistries.get( name );
            if ( subRegistry == null ) {
                return null; // No subregistry found.
            }
            unregisterSubRegistry( subRegistry );
            if ( subRegistry.subRegistries.isEmpty() && subRegistry.placeholders.isEmpty() ) { // No sub-subregistries
                                                                                               // or
                                                                                               // placeholders.
                // Delete all ancestors that are placeholders and became irrelevant.
                cleanPlaceholders();
            } else { // Needs to keep the sub-subregistries and placeholders.
                LOG.debug( "Leaving placeholder." );
                PlaceholderCommandRegistry placeholder = new PlaceholderCommandRegistry( name );
                placeholder.transferSubRegistries( subRegistry );
                placeholders.put( name, placeholder );
                placeholder.setRegistry( this );
            }
            return subRegistry;

        }

    }

    /**
     * Removes the subregistry with the given name, if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, they are kept with
     * the registry and thus also deleted.<br>
     * If the subregistries should be kept in a placeholder for when/if the
     * subregistry is recreated, use {@link #removeSubRegistry(String)}.
     *
     * @param name
     *            The name of the subregistry.
     * @return The deleted subregistry, or <tt>null</tt> if no matching subregistry
     *         found.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     */
    public CommandRegistry removeSubRegistryFull( String name ) throws NullPointerException {

        if ( name == null ) {
            throw new NullPointerException( "Name cannot be null." );
        }

        CommandRegistry subRegistry;
        synchronized ( subRegistries ) {

            subRegistry = subRegistries.get( name );
            if ( subRegistry == null ) {
                return null; // No subregistry found.
            }
            unregisterSubRegistry( subRegistry );

        }

        // Delete all ancestors that are placeholders and became irrelevant.
        cleanPlaceholders();
        return subRegistry;

    }

    /**
     * Removes the subregistry named after the given class (as given by
     * {@link #getNameFromClass(Class)}), if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, leaves a
     * placeholder for it that keeps the subregistries, so that if the subregistry
     * is recreated, the subregistries (and placeholders) are brought back.<br>
     * Thus, if a registry is found and deleted, it will always be returned with no
     * subregistries or placeholders, as any it might have had are left with the
     * placeholder.<br>
     * If the subregistries should be also deleted/kept with the registry, use
     * {@link #removeSubRegistryFull(Class)}.
     *
     * @param clazz
     *            The class that the registry should be named after.
     * @return The deleted subregistry, or <tt>null</tt> if no matching subregistry
     *         found.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     */
    public CommandRegistry removeSubRegistry( Class<?> clazz ) throws NullPointerException {

        return removeSubRegistry( getNameFromClass( clazz ) );

    }

    /**
     * Removes the subregistry named after the given class (as given by
     * {@link #getNameFromClass(Class)}), if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, they are kept with
     * the registry and thus also deleted.<br>
     * If the subregistries should be kept in a placeholder for when/if the
     * subregistry is recreated, use {@link #removeSubRegistry(Class)}.
     *
     * @param clazz
     *            The class that the registry should be named after.
     * @return The deleted subregistry, or <tt>null</tt> if no matching subregistry
     *         found.
     * @throws NullPointerException
     *             if the given name is <tt>null</tt>.
     */
    public CommandRegistry removeSubRegistryFull( Class<?> clazz ) throws NullPointerException {

        return removeSubRegistryFull( getNameFromClass( clazz ) );

    }

    /**
     * Removes the subregistry with the given name that is linked to an object of
     * the given class, if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, leaves a
     * placeholder for it that keeps the subregistries, so that if the subregistry
     * is recreated, the subregistries (and placeholders) are brought back.<br>
     * Thus, if a registry is found and deleted, it will always be returned with no
     * subregistries or placeholders, as any it might have had are left with the
     * placeholder.<br>
     * If the subregistries should be also deleted/kept with the registry, use
     * {@link #removeSubRegistryFull(Class, String)}.
     *
     * @param linkedClass
     *            The class of objects that the desired registry links to.
     * @param name
     *            The simple name of the subregistry.
     * @return The deleted subregistry, or null if no matching subregistry found.
     * @throws NullPointerException
     *             if one of the arguments is null.
     * @throws IllegalArgumentException
     *             if there is no registry for the given type.
     * 
     * @deprecated Use {@link #removeSubRegistry(Class)} instead.
     */
    @Deprecated
    protected CommandRegistry removeSubRegistry( Class<?> linkedClass, String name )
            throws NullPointerException, IllegalArgumentException {

        return removeSubRegistry( linkedClass );

    }

    /**
     * Removes the subregistry with the given name that is linked to an object of
     * the given class, if it exists.
     * <p>
     * If the subregistry has subregistries and/or placeholders, they are kept with
     * the registry and thus also deleted.<br>
     * If the subregistries should be kept in a placeholder for when/if the
     * subregistry is recreated, use {@link #removeSubRegistry(Class, String)}.
     *
     * @param linkedClass
     *            The class of objects that the desired registry links to.
     * @param name
     *            The simple name of the subregistry.
     * @return The deleted subregistry, or null if no matching subregistry found.
     * @throws NullPointerException
     *             if one of the arguments is null.
     * @throws IllegalArgumentException
     *             if there is no registry for the given type.
     * 
     * @deprecated Use {@link #removeSubRegistryFull(Class)} instead.
     */
    @Deprecated
    protected CommandRegistry removeSubRegistryFull( Class<?> linkedClass, String name )
            throws NullPointerException, IllegalArgumentException {

        return removeSubRegistryFull( linkedClass );

    }

    /**
     * Retrieves the subregistry linked to a given object under the given type, that
     * has the given name.
     * <p>
     * If no such subregistry is registered, creates one, initializing from a
     * placeholder if there is one.
     *
     * @param linkedObject
     *            The object whose linked subregistry should be retrieved.
     * @param linkedClass
     *            The specific class that the registry type links to.
     * @param name
     *            The name of the registry.
     * @param <T>
     *            The type of the object.
     * @return The subregistry linked to the given object.
     * @throws NullPointerException
     *             if one of the arguments is null.
     * @throws IllegalArgumentException
     *             if there is no registry for the given type.
     * 
     * @deprecated Use {@link #getSubRegistry(Class)} instead.
     */
    @Deprecated
    public <T> CommandRegistry getSubRegistry( T linkedObject, Class<? super T> linkedClass, String name )
            throws NullPointerException, IllegalArgumentException {

        return getSubRegistry( linkedClass );

    }

    /**
     * Retrieves the subregistry linked to a given module.
     * <p>
     * If no such subregistry is registered, creates one.
     *
     * @param module
     *            The module whose linked subregistry should be retrieved.
     * @return The subregistry linked to the given module.
     * @throws NullPointerException
     *             if the module passed in is null.
     * 
     * @deprecated Use {@link #getSubRegistry(Class)} instead.
     */
    @Deprecated
    public CommandRegistry getSubRegistry( IModule module ) throws NullPointerException {

        return getSubRegistry( module.getClass() );

    }

    /**
     * Determines if there is a subregistry registered in this registry that is
     * linked to the given module.
     *
     * @param module
     *            The module to check for.
     * @return true if there is a subregistry linked to the given module. false
     *         otherwise.
     * @throws NullPointerException
     *             if the module passed in is null.
     * 
     * @deprecated Use {@link #hasSubRegistry(Class)} instead.
     */
    @Deprecated
    public boolean hasSubRegistry( IModule module ) throws NullPointerException {

        return hasSubRegistry( module.getClass() );

    }

    /**
     * Removes the subregistry that is linked to the given module from this
     * registry, if there is one.
     * <p>
     * After removing the subregistry, the next to call to
     * {@link #getSubRegistry(IModule)} using the given module will create a new
     * subregistry, but with the same sub-subregistries.
     *
     * @param module
     *            The module whose linked subregistry is to be removed.
     * @return The (removed) subregistry that is linked to the given module, or
     *         <tt>null</tt> if there was no such subregistry.
     * @throws NullPointerException
     *             if the module passed in is <tt>null</tt>.
     * 
     * @deprecated Use {@link #removeSubRegistry(Class)} instead.
     */
    @Deprecated
    public CommandRegistry removeSubRegistry( IModule module ) throws NullPointerException {

        return removeSubRegistry( module.getClass() );

    }

    /**
     * Retrieves the subregistries registered in this registry.
     *
     * @return The registered subregistries.
     */
    public NavigableSet<CommandRegistry> getSubRegistries() {

        return new TreeSet<>( subRegistries.values() );

    }

    @Override
    public CommandRegistry getRegistry() {

        return parentRegistry;

    }

    /**
     * Retrieves the registry that is the root of the calling registry's inheritance
     * chain.<br>
     * That is, retrieves this registry's farthest parent registry, the first in the
     * chain that is not registered to any registry.
     *
     * @return The root of this registry's inheritance chain.
     */
    public CommandRegistry getRoot() {

        CommandRegistry cur = this;
        CommandRegistry parent = cur.getRegistry();
        while ( parent != null ) {
            cur = parent;
            parent = cur.getRegistry();
        }
        return cur;

    }

    @Override
    public synchronized void setRegistry( CommandRegistry registry ) {

        this.parentRegistry = registry;

    }

    /**
     * Sets the prefix of this registry.
     *
     * @param prefix
     *            The prefix to use for this registry.
     */
    public void setPrefix( String prefix ) {

        this.prefix = prefix;
        LOG.debug( "Setting prefix of \"{}\" to \"{}\".", getPath(), prefix );
        setLastChanged( System.currentTimeMillis() );

    }

    @Override
    public String getPrefix() {

        return prefix;

    }

    /**
     * Retrieves the <i>effective</i> prefix of the registry. If the registry does
     * not declare its own prefix, uses the prefix of the parent registry. If there
     * is no parent registry, uses the {@link #DEFAULT_PREFIX default prefix}.
     */
    @Override
    public String getEffectivePrefix() {

        if ( getPrefix() != null ) {
            return getPrefix();
        } else {
            CommandRegistry parent = getRegistry();
            return parent != null ? parent.getEffectivePrefix() : CommandRegistry.DEFAULT_PREFIX;
        }

    }

    @Override
    public boolean isEnabled() {

        return enabled;

    }

    @Override
    public void setEnabled( boolean enabled ) throws IllegalStateException {

        if ( isEssential() && !enabled ) {
            throw new IllegalStateException( "Attempted to disable an essential registry." );
        }
        LOG.debug( "Setting {} to {}.", getPath(), enabled ? "enabled" : "disabled" );
        this.enabled = enabled;

    }

    @Override
    public boolean isEssential() {

        return essential;

    }

    /**
     * Sets whether this registry is essential.
     *
     * @param essential
     *            If <tt>true</tt>, the registry is marked as essential. If
     *            <tt>false</tt>, it is marked as not essential.
     * @see #isEssential()
     */
    public void setEssential( boolean essential ) {

        this.essential = essential;

    }

    /**
     * Sets an operation to be ran to determine if this registry is active under the
     * context of a command.
     * <p>
     * By default there is no check operation, so the registry is active for any
     * context. This state can be restored by passing in <tt>null</tt> to this
     * method.
     * <p>
     * If a registry is not active under a command context, then a command called
     * under that context will not be executed, thus having the same effect as if it
     * was disabled.
     * <p>
     * This can be used to create functionality where commands in certain registries
     * are only available (or not available) if the context, or some other arbitrary
     * internal state of the program, matches a certain criteria.<br>
     * Essentially a runtime-configurable way of making a registry conditionally
     * enabled or disabled depending on the context that its commands are called
     * from and the state of the program.<br>
     * However, this does <i>not</i> replace enabled/disabled functionality. If the
     * registry was set as disabled, no commands from it will be executed,
     * independent of any context checks. The check is only called if the registry
     * is enabled.
     *
     * @param contextCheck
     *            The check to run on the context of commands. If <tt>null</tt>,
     *            removes the current context check.
     * @see #contextCheck(CommandContext)
     */
    public void setContextCheck( Predicate<CommandContext> contextCheck ) {

        synchronized ( contextChecks ) {

            contextChecks.clear();
            if ( contextCheck != null ) { // set context check.
                this.contextCheck = contextCheck;
                LOG.debug( "Setting context check for \"{}\".", getPath() );
                contextChecks.add( contextCheck );
            } else { // Remove context check.
                this.contextCheck = NO_CHECK;
                LOG.debug( "Removing all context checks for \"{}\".", getPath() );
            }

        }

    }

    /**
     * Adds a context check to run in addition to the currently set context
     * check.<br>
     * The context check becomes a composition of the currently set context check
     * AND the given context check.
     * <p>
     * If there is no currently set context check, the given check becomes the only
     * one.
     *
     * @param contextCheck
     *            The context check to be ran in addition to the current one.
     * @throws NullPointerException
     *             if the context check given is null.
     * @see #setContextCheck(Predicate)
     * @see Predicate#and(Predicate)
     */
    public void addContextCheck( Predicate<CommandContext> contextCheck ) throws NullPointerException {

        if ( contextCheck == null ) {
            throw new NullPointerException( "Context check to be added cannot be null." );
        }

        synchronized ( contextChecks ) {

            LOG.debug( "Adding context check for \"{}\".", getPath() );
            if ( contextChecks.isEmpty() ) { // This is the first context check.
                this.contextCheck = contextCheck;
            } else { // This is not the first context check.
                this.contextCheck = this.contextCheck.and( contextCheck );
            }
            contextChecks.add( contextCheck );

        }

    }

    /**
     * Removes one of the context checks being made for this registry. The context
     * check becomes a logical AND of all the remaining context checks.
     * <p>
     * If there are no remaining context checks, the context check is removed.
     *
     * @param contextCheck
     *            The context check to be removed.
     * @throws NullPointerException
     *             if the context check given is null.
     * @see #addContextCheck(Predicate)
     */
    public void removeContextCheck( Predicate<CommandContext> contextCheck ) throws NullPointerException {

        if ( contextCheck == null ) {
            throw new NullPointerException( "Context check to be removed cannot be null." );
        }

        synchronized ( contextChecks ) {

            LOG.debug( "Removing context check for \"{}\".", getPath() );

            if ( contextChecks.remove( contextCheck ) ) {
                if ( contextChecks.isEmpty() ) {
                    this.contextCheck = NO_CHECK; // No more context checks.
                    return;
                } else {
                    /* Remakes context check from remaining checks */
                    Iterator<Predicate<CommandContext>> iter = contextChecks.iterator();
                    Predicate<CommandContext> composedCheck = iter.next();
                    while ( iter.hasNext() ) {

                        composedCheck = composedCheck.and( contextCheck );

                    }
                    this.contextCheck = composedCheck;
                }
            }

        }

    }

    /**
     * Retrieves the operation being used to determine if this registry is active
     * under the context of a command.
     * <p>
     * If there are multiple context checks set, the returned object is a logical
     * AND of all the set context checks.
     *
     * @return The check being ran on command contexts, or <tt>null</tt> if no check
     *         is set.
     * @see #addContextCheck(Predicate)
     */
    public Predicate<CommandContext> getContextCheck() {

        synchronized ( contextChecks ) {

            return contextChecks.isEmpty() ? null : contextCheck;

        }

    }

    /**
     * Retrieves the operations being used to determine if this registry is active
     * under the context of a command.
     *
     * @return The list of context checks. May be empty if no check is set.
     */
    public List<Predicate<CommandContext>> getContextChecks() {

        synchronized ( contextChecks ) {

            return new ArrayList<>( contextChecks );

        }

    }

    /**
     * Retrieves whether this registry is active under the given context.
     * <p>
     * This method runs the check operation set through
     * {@link #setContextCheck(Predicate)}.<br>
     * If none were set, this is always true.
     * <p>
     * If a registry fails its context check and thus is not active, all the
     * subregistries registered under it are not active.<br>
     * This means that if some registry above this in the registry hierarchy is not
     * active under the given context, this registry is also not active, independent
     * of the result of its own context check.
     * <p>
     * If a registry is not active under a command context, then a command called
     * under that context will not be executed, thus having the same effect as if it
     * was disabled.
     * 
     * @param context
     *            The context to check if the registry is active for.
     * @return <tt>true</tt> if this registry is active under the given context
     *         (passed context check).<br>
     *         <tt>false</tt> if it is not active (failed context check).
     * @see #setContextCheck(Predicate)
     */
    public boolean contextCheck( CommandContext context ) {

        CommandRegistry parent = getRegistry();
        return contextCheck.test( context ) && ( ( parent == null ) || parent.contextCheck( context ) );

    }

    /**
     * Compares two CommandRegistries.
     * <p>
     * Registries are compared using their names. In case of ties, their qualifiers
     * are compared.
     * <p>
     * Used to sort their display order.
     *
     * @param cr
     *            The registry to compare to.
     * @return A negative value if this registry is lesser than the given registry
     *         (comes first). A positive value if this registry is greater than the
     *         given registry (comes after). Zero if both are equal (either can come
     *         first).
     * @see String#compareTo(String)
     */
    @Override
    public int compareTo( CommandRegistry cr ) {

        return this.getName().compareTo( cr.getName() );

    }

    /**
     * Registers a command into the calling registry.<br>
     * The command will fail to be added if there is already a command in the
     * registry hierarchy (parent and sub registries) with the same name (eg the
     * command name must be unique). (placeholder subregistries are also counted).
     * <p>
     * If the command was already registered to another registry (that is not part
     * of the hierarchy of the calling registry), it is unregistered from it
     * first.<br>
     * If it failed to be registered to this registry, it is not unregistered from
     * the previous registry.
     * <p>
     * Only main commands can be registered. Subcommands will simply fail to be
     * added.
     *
     * @param command
     *            The command to be registered.
     * @return true if the command was registered successfully, false if it could
     *         not be registered.
     * @throws NullPointerException
     *             if the command passed in is null.
     */
    public boolean registerCommand( ICommand command ) throws NullPointerException {

        LOG.info( "Attempting to register command {} to \"{}\".", getCommandString( command ), getPath() );

        /* Check for fail cases */
        boolean fail = false;
        if ( command.isSubCommand() ) {
            fail = true; // Sub commands cannot be registered directly.
        } else if ( getRoot().getCommand( command.getName() ) != null ) {
            fail = true; // Check if there is a command in the chain with the same name.
        }
        if ( fail ) {
            LOG.error( "Failed to register command \"{}\".", command.getName() );
            return false; // Error found.
        }

        synchronized ( commands ) {

            synchronized ( command ) {

                if ( command.getRegistry() != null ) { // Unregister from current registry if any.
                    command.getRegistry().unregisterCommand( command );
                }
                command.setRegistry( this );

            }

            commands.put( command.getName(), command ); // Add command to main table.

            /* Add identifiers to the appropriate table */
            Collection<String> identifiers;
            Map<String, PriorityQueue<ICommand>> commandTable;
            if ( command.getPrefix() != null ) { // Command specifies a prefix.
                identifiers = command.getSignatures();
                commandTable = withPrefix; // Add signatures to table of commands with prefix.
            } else { // Command does not specify a prefix.
                identifiers = command.getAliases();
                commandTable = noPrefix; // Add aliases to table of commands without prefix.
            }
            for ( String identifier : identifiers ) { // For each identifier (signature or alias).

                PriorityQueue<ICommand> queue = commandTable.get( identifier ); // Get queue of commands with
                if ( queue == null ) { // that identifier.
                    queue = new PriorityQueue<>(); // If none, initialize it.
                    commandTable.put( identifier, queue );
                }
                queue.add( command ); // Add command to list of commands with that identifier.

            }

        }
        LOG.info( "Registered command \"{}\".", command.getName() );
        setLastChanged( System.currentTimeMillis() );
        return true;

    }

    /**
     * Attempts to register all the commands in the given collection to this
     * registry.
     * <p>
     * After the method returns, all the commands that did not fail to be registered
     * will be registered to this registry.<br>
     * They will also be unregistered from their previous registry, if any.
     * <p>
     * Commands that fail to be registered are unchanged.
     *
     * @param commands
     *            The commands to be registered.
     */
    public void registerAllCommands( Collection<ICommand> commands ) {

        for ( ICommand command : commands ) { // Register each command.

            try {
                registerCommand( command );
            } catch ( NullPointerException e ) {
                LOG.error( "Command collection included null.", e );
            }

        }

    }

    /**
     * Attempts to register all the commands in the given registry to this registry.
     * <p>
     * After the method returns, all the commands that did not fail to be registered
     * will be registered to this registry.<br>
     * They will also be unregistered from the given registry.
     * <p>
     * Commands that fail to be registered are unchanged.
     *
     * @param registry
     *            The registry to get commands from.
     */
    public void registerAllCommands( CommandRegistry registry ) {

        registerAllCommands( registry.getRegisteredCommands() );

    }

    /**
     * Parses the annotated commands from the given object and registers the parsed
     * main commands into this registry.
     *
     * @param obj
     *            The object to parse commands from.
     */
    public void registerAnnotatedCommands( Object obj ) {

        AnnotationParser parser = new AnnotationParser( obj );
        registerAllCommands( parser.parse() ); // Parse an add all commands.

    }

    /**
     * Unregisters a command from this registry.
     *
     * @param command
     *            The command to be unregistered.
     * @return true if the command was unregistered successfully;<br>
     *         false if it was not registered in this registry.
     */
    public boolean unregisterCommand( ICommand command ) {

        if ( command == null ) {
            return false; // Received null command.
        }
        if ( LOG.isInfoEnabled() ) {
            LOG.info( "Attempting to deregister command {} from \"{}\".", getCommandString( command ), getPath() );
        }

        synchronized ( commands ) {

            if ( commands.get( command.getName() ) != command ) {
                LOG.error( "Failed to deregister command \"{}\".", command.getName() );
                return false; // No command with this name, or the command registered with this name was not
            } // the one given.
            commands.remove( command.getName() );

            /* Remove identifiers from the appropriate table */
            Collection<String> identifiers;
            Map<String, PriorityQueue<ICommand>> commandTable;
            if ( command.getPrefix() != null ) { // Command specifies a prefix.
                identifiers = command.getSignatures();
                commandTable = withPrefix; // Add signatures to table of commands with prefix.
            } else { // Command does not specify a prefix.
                identifiers = command.getAliases();
                commandTable = noPrefix; // Add aliases to table of commands without prefix.
            }
            for ( String identifier : identifiers ) { // For each identifier (signature or alias).

                commandTable.get( identifier ).remove( command ); // Remove command from the queue of commands with
                                                                  // that identifier.
            }

        }

        command.setRegistry( null );
        LOG.info( "Deregistered command \"{}\".", command.getName() );
        setLastChanged( System.currentTimeMillis() );
        return true;

    }

    /**
     * Creates a string that describes the essential information of a given command.
     * Includes name, prefix, aliases, description, and usage.
     *
     * @param command
     *            The command to be described.
     * @return A string that describes the main info of the command.
     */
    private static String getCommandString( ICommand command ) {

        StringBuilder builder = new StringBuilder();
        builder.append( '"' );
        builder.append( command.getName() );
        builder.append( "\"::(" );
        builder.append( ( command.getPrefix() != null ) ? command.getPrefix() : "" );
        builder.append( ')' );
        builder.append( command.getAliases() );
        builder.append( "::<\"" );
        builder.append( command.getDescription() );
        builder.append( "\">::<\"" );
        builder.append( command.getUsage() );
        builder.append( "\">" );
        return builder.toString();

    }

    /**
     * Deregisters all the commands in the registry.
     */
    public void clear() {

        LOG.info( "Clearing all commands in registry {}.", getPath() );
        commands.clear();
        withPrefix.clear();
        noPrefix.clear();

    }

    /**
     * Retrieves the command registered in this registry that has the given name, if
     * one exists.
     *
     * @param name
     *            The name of the command.
     * @return The command in this registry with the given name, or null if there is
     *         no such command.
     */
    public ICommand getRegisteredCommand( String name ) {

        return commands.get( name );

    }

    /**
     * Retrieves all commands registered in this registry.
     * <p>
     * The returned set is sorted by the lexicographical order of the command names.
     *
     * @return The commands registered in this registry.
     */
    public NavigableSet<ICommand> getRegisteredCommands() {

        NavigableSet<ICommand> commands = new TreeSet<>( ( c1, c2 ) -> {
            // Compare elements by their names.
            return c1.getName().compareTo( c2.getName() );

        } );
        commands.addAll( this.commands.values() );
        return commands;

    }

    /**
     * Retrieves the command registered in this registry or its subregistries that
     * has the given name, if one exists.
     * <p>
     * The search on the subregistries also includes their respective subregistries
     * (eg searches recursively). Placeholder subregistries are also searched.
     *
     * @param name
     *            The name of the command.
     * @return The command in this registry or its subregistries with the given
     *         name, or null if there is no such command.
     */
    public ICommand getCommand( String name ) {

        ICommand command = getRegisteredCommand( name );
        if ( command != null ) {
            return command; // Found command in this registry.
        }
        for ( CommandRegistry subRegistry : subRegistries.values() ) { // Check each subregistry.

            command = subRegistry.getCommand( name );
            if ( command != null ) {
                return command; // Found command in a subregistry.
            }

        }
        for ( CommandRegistry placeholder : placeholders.values() ) { // Check each placeholder.

            command = placeholder.getCommand( name );
            if ( command != null ) {
                return command; // Found command in a subregistry.
            }

        }
        return null; // Command not found.

    }

    /**
     * Retrieves all commands registered in this registry or its subregistries.
     * <p>
     * The returned set is sorted by the lexicographical order of the command names.
     * <p>
     * The search on the subregistries also includes their respective subregistries
     * (eg searches recursively).
     *
     * @return The commands registered in this registry or its subregistries.
     */
    public NavigableSet<ICommand> getCommands() {

        NavigableSet<ICommand> commands = getRegisteredCommands();
        for ( CommandRegistry subRegistry : subRegistries.values() ) {
            // Add commands from subregistries.
            commands.addAll( subRegistry.getCommands() );

        }
        return commands;

    }

    /**
     * Retrieves the command, in this registry or one of its subregistries
     * (recursively), whose signature matches the signature given.
     *
     * @param signature
     *            Signature to be matched.
     * @param enableLogging
     *            Whether log messages should be enabled. Set this to <tt>false</tt>
     *            when doing multiple queries programmatically to avoid even more
     *            spam at the trace level.
     * @return The command with the given signature, or <tt>null</tt> if none found
     *         in this registry or one of its subregistries.
     */
    public ICommand parseCommand( String signature, boolean enableLogging ) {

        if ( enableLogging ) {
            LOG.trace( "Parsing \"{}\" in registry \"{}\".", signature, getPath() );
        }
        ICommand command = null;

        /* Search this registry */
        PriorityQueue<ICommand> commandQueue = withPrefix.get( signature );
        if ( commandQueue != null ) { // Found a queue of commands with the given signature.
            command = commandQueue.peek(); // Get the first one.
        }
        String registryPrefix = getEffectivePrefix();
        if ( signature.startsWith( registryPrefix ) ) { // Command has the same prefix as the registry.
            // Remove the prefix and check the commands with no specified prefix.
            commandQueue = noPrefix.get( signature.substring( registryPrefix.length() ) );
            if ( commandQueue != null ) { // Found commands with same alias.
                ICommand candidate = commandQueue.peek(); // Get the first one.
                if ( ( candidate != null ) && ( ( command == null ) || ( candidate.compareTo( command ) < 0 ) ) ) {
                    command = candidate; // Keeps it if it has higher precedence than the current command.
                }
            }
        }
        if ( ( command != null ) && !command.isOverrideable() ) {
            if ( enableLogging ) {
                LOG.trace( "Registry \"{}\" found: \"{}\" (not overrideable).", getPath(), command.getName() );
            }
            return command; // Found a command and it can't be overriden.
        }

        /* Check if a subregistry has the command */
        ICommand subCommand = null;
        for ( CommandRegistry subRegistry : subRegistries.values() ) {

            ICommand candidate = subRegistry.parseCommand( signature, enableLogging );
            if ( ( candidate != null ) && ( ( subCommand == null ) || ( candidate.compareTo( subCommand ) < 0 ) ) ) {
                subCommand = candidate; // Keeps it if it has higher precedence than the current command.
            }

        }
        if ( subCommand != null ) {
            command = subCommand; // A command from a subregistry always has higher precedence than
        } // the current registry.

        if ( enableLogging ) {
            LOG.trace( "Registry \"{}\" found: {}.", getPath(),
                    ( command == null ) ? null : ( "\"" + command.getName() + "\"" ) );
        }
        return command; // Returns what was found in this registry. Will be null if not found in this
                        // registry.
    }

    /**
     * Retrieves the command, in this registry or one of its subregistries
     * (recursively), whose signature matches the signature given.
     * <p>
     * Logging is enabled through this method.
     *
     * @param signature
     *            Signature to be matched.
     * @return The command with the given signature, or null if none found in this
     *         registry or one of its subregistries.
     * @see #parseCommand(String, boolean)
     */
    public ICommand parseCommand( String signature ) {

        return parseCommand( signature, true );

    }

    /**
     * Sets the last time when this registry or one of its subregistries was
     * changed.
     * <p>
     * Automatically sets the lastChanged value on the registry it is registered to,
     * if there is one.
     *
     * @param lastChanged
     *            The time, in milliseconds from epoch, that this registry or one of
     *            its subregistries had a change.
     * @see #getLastChanged()
     */
    protected void setLastChanged( long lastChanged ) {

        this.lastChanged = lastChanged;
        CommandRegistry parent = getRegistry();
        if ( parent != null ) {
            parent.setLastChanged( lastChanged );
        }

    }

    /**
     * Gets the last time when this registry or one of its subregistries was
     * changed.
     * <p>
     * Changes counted by this include:
     * <ul>
     * <li>Registering or de-registering a command;</li>
     * <li>Registering or de-registering a subregistry.</li>
     * <li>Changing the prefix of the subregistry.</li>
     * </ul>
     *
     * @return The time of the last change, in milliseconds from epoch.
     * @see System#currentTimeMillis()
     */
    public long getLastChanged() {

        return lastChanged;

    }

}
