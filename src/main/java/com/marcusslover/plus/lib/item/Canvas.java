package com.marcusslover.plus.lib.item;

import com.marcusslover.plus.lib.text.Text;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A canvas is a representation of a menu.
 * It is used to customize the menu layout.
 */
@Data
@Accessors(fluent = true, chain = true)
public class Canvas {
    private @NotNull Integer rows; // 1-6 (using non-primitive to allow @NotNull for the constructor)
    private @Nullable Component title;
    private @Nullable Inventory assosiatedInventory = null;
    private @Nullable GenericClick genericClick = null;
    private @Nullable SelfInventory selfInventory = null;
    private @Nullable Canvas.PopulatorContext.ViewStrategy viewStrategy = null;

    // buttons of the canvas
    private final @NotNull List<Button> buttons = new ArrayList<>();

    /**
     * Set the title of the canvas.
     *
     * @param title the title
     * @return the canvas
     */
    public @NotNull Canvas title(@Nullable Component title) {
        this.title = title;
        return this;
    }

    /**
     * Set the title of the canvas.
     *
     * @param title the title
     * @return the canvas
     */
    public @NotNull Canvas title(@Nullable Text title) {
        if (title != null) {
            this.title = title.comp();
        } else {
            this.title = null;
        }
        return this;
    }

    /**
     * Set the title of the canvas.
     *
     * @param title the title
     * @return the canvas
     */
    public @NotNull Canvas title(@Nullable String title) {
        if (title != null) {
            this.title = Text.of(title).comp();
        } else {
            this.title = null;
        }
        return this;
    }

    /**
     * Craft the inventory.
     *
     * @return the inventory
     */
    public @NotNull Inventory craftInventory() {
        if (this.title == null) {
            return Bukkit.createInventory(null, this.rows * 9);
        }
        return Bukkit.createInventory(null, this.rows * 9, this.title);
    }

    /**
     * Add a button to the canvas.
     *
     * @param button      the button
     * @param buttonClick the button click
     * @return the click context associated with the button
     */
    public @NotNull ClickContext button(@NotNull Button button, @Nullable Canvas.ButtonClick buttonClick) {
        this.buttons.add(button);
        ClickContext context = new ClickContext(buttonClick);
        button.clickContext(context);
        return context;
    }

    /**
     * Starts the population of the elements on the canvas.
     *
     * @param elements the elements
     * @param <T>      the type of the elements
     * @return the canvas
     */
    public <T> @NotNull PopulatorContext<T> populate(@NotNull List<T> elements) {
        return new PopulatorContext<>(this, elements);
    }

    /**
     * Represents the context of the population.
     * It holds the elements and the canvas.
     * It also holds the view strategy.
     *
     * @param <T> the type of the elements
     */
    @Data
    public static class PopulatorContext<T> {
        private final @NotNull Canvas canvas;
        private final @NotNull List<T> elements;
        private @Nullable ViewStrategy viewStrategy = null;

        /**
         * Set the view strategy.
         * Works only with the default view strategies.
         *
         * @param defaultViewStrategy the view strategy
         * @return the populator context
         */
        public @NotNull PopulatorContext<T> viewStrategy(@Nullable DefaultViewStrategy defaultViewStrategy) {
            this.viewStrategy = defaultViewStrategy.viewStrategy();
            return this;
        }

        /**
         * Modify how the elements are populated on the canvas.
         *
         * @param populator the populator
         * @return the populator context
         */
        public @NotNull PopulatorContext<T> content(@NotNull Populator<T> populator) {
            int counter = 0;
            for (T element : this.elements) {
                Button button = Button.create(counter);

                if (this.viewStrategy != null) {
                    this.viewStrategy.handle(counter, this.canvas, button);
                }
                populator.populate(element, this.canvas, button);
                counter++;
            }
            return this;
        }

        /**
         * Return to the canvas.
         *
         * @return the canvas
         */
        public @NotNull Canvas end() {
            return this.canvas;
        }

        /**
         * Populates one element and provides the player with the button.
         *
         * @param <T> the type of the element
         */
        @FunctionalInterface
        public interface Populator<T> {

            /**
             * Populates the element.
             *
             * @param element the element to populate
             * @param canvas the canvas
             * @param button  the button associated with the element on the canvas
             */
            void populate(@NotNull T element, @NotNull Canvas canvas, @NotNull Button button);
        }

        /**
         * Holds the strategy on how to populate the elements on the canvas.
         * If a menu requires multiple pages, this is the strategy to use.
         * The strategy allows the modification of the area in which the elements are populated.
         */
        @FunctionalInterface
        public interface ViewStrategy {

            /**
             * Allows you to modify the button depending on the given context.
             *
             * @param counter the counter
             * @param canvas  the canvas
             * @param button  the button to modify if needed
             */
            void handle(int counter, @NotNull Canvas canvas, @NotNull Button button);
        }

        /**
         * Default view strategies.
         */
        @Accessors(fluent = true, chain = true)
        public enum DefaultViewStrategy {
            /**
             * Fills all first possible slots on the canvas.
             */
            FULL((counter, canvas, button) -> button.slot(counter)),

            /**
             * Fills all first possible middle slots on the canvas.
             */
            MIDDLE((counter, canvas, button) -> {
                List<Integer> middleSlots = DefaultViewStrategy.middleSlots(canvas.rows());
                if (middleSlots.size() > counter) {
                    button.slot(middleSlots.get(counter));
                }
            });

            @Getter
            private final ViewStrategy viewStrategy;

            DefaultViewStrategy(ViewStrategy viewStrategy) {
                this.viewStrategy = viewStrategy;
            }

            /**
             * Gets all middle slots from the given sized menu.
             * The middle slots are the slots that are not on the edges.
             *
             * @param rows the rows
             * @return the middle slots
             */
            public static List<Integer> middleSlots(int rows) {
                /* Check if the rows is at least > 2
                 * [] = Not needed
                 * {} = Middle parts
                 *
                 * Rows:
                 *    [] [] [] [] [] [] [] [] [] - Row 1
                 *    [] {} {} {} {} {} {} {} [] - Row 2
                 *    [] [] [] [] [] [] [] [] [] - Row 3
                 */
                if (rows > 2) {
                    // calculate all the middle rows
                    int middleRows = (rows / 9) - 2;
                    List<Integer> integers = new ArrayList<>();

                    // loop through all middle rows
                    for (int i = 0; i < middleRows; i++) {
                        int slot = 10 + (9 * i); // fill with all rest iteration

                        for (int j = 0; j < 7; j++) {
                            integers.add((slot + j));
                        }
                    }

                    return integers;
                }

                return new ArrayList<>();

            }
        }
    }


    /**
     * A button click context.
     */
    @Data
    public static class ClickContext {
        private final @Nullable ButtonClick click;
        private @Nullable Consumer<Throwable> throwableConsumer;

        public @NotNull ClickContext handleException(@NotNull Consumer<Throwable> exception) {
            this.throwableConsumer = exception;
            return this;
        }
    }

    /**
     * A button click. This is called when a player clicks on the button.
     */
    @FunctionalInterface
    public interface ButtonClick {
        /**
         * Called when a target clicks on the button.
         *
         * @param target the target
         * @param event  the event
         */
        void onClick(@NotNull Player target, @NotNull Item clicked, @NotNull InventoryClickEvent event);
    }

    /**
     * A generic click. This is called when a player clicks on the inventory.
     * This is called before the button click.
     */
    @FunctionalInterface
    public interface GenericClick {
        /**
         * Called when a player clicks on the inventory.
         *
         * @param target the player
         * @param event  the event
         * @param canvas the canvas
         */
        void onClick(@NotNull Player target, @NotNull Item clicked, @NotNull InventoryClickEvent event, @NotNull Canvas canvas);
    }

    /**
     * A self inventory click. This is called when a player clicks on their inventory.
     */
    @FunctionalInterface
    public interface SelfInventory {
        /**
         * Called when a player clicks on their inventory.
         *
         * @param target the player
         * @param event  the event
         * @param canvas the canvas
         */
        void onClick(@NotNull Player target, @NotNull Item clicked, @NotNull InventoryClickEvent event, @NotNull Canvas canvas);
    }
}