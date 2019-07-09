package org.eientei.gtce2oc.driver;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import li.cil.oc.common.recipe.ExtendedShapedOreRecipe;
import li.cil.oc.common.recipe.ExtendedShapelessOreRecipe;
import li.cil.oc.common.recipe.Recipes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.GameData;
import scala.Char;
import scala.Function2;
import scala.Option;
import scala.runtime.AbstractFunction2;
import scala.runtime.BoxedUnit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.eientei.gtce2oc.GTCE2OC.logger;

public class RecipeIntegration {
    public static class HandlerWrapper extends AbstractFunction2<ItemStack, Config, BoxedUnit> {
        private Consumer<ParsedRecipe> handler;

        public HandlerWrapper(Consumer<ParsedRecipe> handler) {
            this.handler = handler;
        }

        @Override
        public BoxedUnit apply(ItemStack output, Config recipe) {
            //handler.accept(parseRecipe(output, recipe));
            return BoxedUnit.UNIT;
        }
    }


    public static void namedList(StringBuilder p, List list) {
        p.append("[");
        boolean first = true;
        for (Object o : list) {
            if (!first) {
                p.append(", ");
            }
            if (o instanceof ItemStack) {
                p.append(((ItemStack) o).getDisplayName());
            } else if (o instanceof FluidStack) {
                p.append(((FluidStack) o).getLocalizedName());
            }
            first = false;
        }
        p.append("]");
    }

    public static void registerRecipeHandlers() throws IllegalAccessException {
        for (Field field : RecipeMaps.class.getDeclaredFields()) {
            if (!field.getType().equals(RecipeMap.class)) {
                continue;
            }
            if (!field.getName().endsWith("_RECIPES")) {
                continue;
            }

            String rawName = field.getName().replace("_RECIPES", "");
            StringBuilder sb = new StringBuilder();
            sb.append("gtce_");
            int[] codepoints = rawName.codePoints().toArray();
            for (int i = 0; i < codepoints.length; i++) {
                int c = codepoints[i];
                if (c == '_') {
                    continue;
                }

                if (i > 0 && codepoints[i - 1] == '_') {
                    sb.appendCodePoint(c);
                } else {
                    sb.appendCodePoint(Character.toLowerCase(c));
                }
            }

            logger.info("Registering recipe handler {}", sb.toString());
            RecipeMap entry = (RecipeMap) field.get(null);
            Recipes.registerRecipeHandler(sb.toString(), new HandlerWrapper(parsed -> {
                StringBuilder p = new StringBuilder();
                if (!parsed.getInputs().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getInputs());
                    p.append(" inputs");
                }
                if (!parsed.getOutputs().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getOutputs());
                    p.append(" outputs");
                }
                if (!parsed.getInputFluids().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getInputFluids());
                    p.append(" fluid inputs");
                }
                if (!parsed.getOutputFluids().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getOutputFluids());
                    p.append(" fluid outputs");
                }
                p.append(" requiring ")
                        .append(parsed.getEu()).append(" EUs and taking ")
                        .append(parsed.getDuration()).append(" ticks");

                logger.info("Registering recipe for handler {} with {}", sb.toString(), p.toString());
                entry.recipeBuilder()
                        .duration(parsed.getDuration())
                        .EUt(parsed.getEu())
                        .inputs(parsed.getInputs())
                        .fluidInputs(parsed.getInputFluids())
                        .outputs(parsed.getOutputs())
                        .fluidOutputs(parsed.getOutputFluids())
                        .buildAndRegister();
            }));
        }

        Recipes.registerRecipeHandler("shaped", new HandlerWrapper(parsed -> {
            if (parsed.getInputs().size() != 9) {
                throw new Recipes.RecipeException("Invalid recipe input size " + parsed.getInputs().size() + " != 9");
            }
            if (parsed.getOutputs().size() != 1) {
                throw new Recipes.RecipeException("Invalid recipe output size " + parsed.getOutputs().size() + " != 1");
            }

            ItemStack output = parsed.getOutputs().get(0);

            Map<Character, ItemStack> ingredients = new HashMap<>();
            List<Object> args = new ArrayList<>();
            boolean nonempty = false;
            for (int i = 0; i < 3; i++) {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < 3; k++) {
                    int n = (i * 3) + k;
                    ItemStack value = parsed.getInputs().get(n);
                    if (value == null) {
                        sb.append(' ');
                    } else{
                        char c = Character.toChars('a' + n)[0];
                        sb.append(c);
                        ingredients.put(c, value);
                        nonempty = true;
                    }
                }
                args.add(sb.toString());
            }
            for (Map.Entry<Character, ItemStack> e : ingredients.entrySet()) {
                args.add(e.getKey());
                args.add(e.getValue());
            }
            if (nonempty && output.getCount() > 0) {
                GameData.register_impl(new ShapedOreRecipe(null, output, args.toArray()));
            }
        }));

        Recipes.registerRecipeHandler("shapeless", new HandlerWrapper(parsed -> {
            ItemStack output = parsed.getOutputs().get(0);

            if (!parsed.getInputs().isEmpty() && output.getCount() > 0) {
                GameData.register_impl(new ShapelessOreRecipe(null, output, parsed.getInputs().toArray()));
            }
        }));

        Recipes.registerRecipeHandler("furnace", new HandlerWrapper(parsed -> {
            ItemStack output = parsed.getOutputs().get(0);
            ItemStack input = parsed.getInputs().get(0);

            FurnaceRecipes.instance().addSmelting(input.getItem(), output, 0);
        }));

        Recipes.registerRecipeHandler("gtce_multiple", new HandlerWrapper(null) {
            @Override
            public BoxedUnit apply(ItemStack output, Config recipe) {
                List<? extends Config> alternatives = recipe.getConfigList("alternatives");
                for (Config altconf : alternatives) {
                    String type = "shaped";
                    if (altconf.hasPath("type")) {
                        type = altconf.getString("type");
                    }
                    Option<Function2<ItemStack, Config, BoxedUnit>> func = Recipes.recipeHandlers().get(type);
                    if (func.isDefined()) {
                        logger.info("Registering recipe alternative for handler {} and output {}", type, output.getDisplayName());
                        func.get().apply(output, altconf);
                    }
                }
                return BoxedUnit.UNIT;
            }
        });
    }

    /*
    public static class HandlerWrapper extends AbstractFunction2<ItemStack, Config, BoxedUnit> {
        private Consumer<ParsedRecipe> handler;

        public HandlerWrapper(Consumer<ParsedRecipe> handler) {
            this.handler = handler;
        }

        @Override
        public BoxedUnit apply(ItemStack output, Config recipe) {
            handler.accept(parseRecipe(output, recipe));
            return BoxedUnit.UNIT;
        }
    }

    public static void namedList(StringBuilder p, List list) {
        p.append("[");
        boolean first = true;
        for (Object o : list) {
            if (!first) {
                p.append(", ");
            }
            if (o instanceof ItemStack) {
                p.append(((ItemStack) o).getDisplayName());
            } else if (o instanceof FluidStack) {
                p.append(((FluidStack) o).getLocalizedName());
            }
            first = false;
        }
        p.append("]");
    }

    public static void registerRecipeHandlers() throws IllegalAccessException {
        for (Field field : RecipeMaps.class.getDeclaredFields()) {
            if (!field.getType().equals(RecipeMap.class)) {
                continue;
            }
            if (!field.getName().endsWith("_RECIPES")) {
                continue;
            }

            String rawName = field.getName().replace("_RECIPES", "");
            StringBuilder sb = new StringBuilder();
            sb.append("gtce_");
            int[] codepoints = rawName.codePoints().toArray();
            for (int i = 0; i < codepoints.length; i++) {
                int c = codepoints[i];
                if (c == '_') {
                    continue;
                }

                if (i > 0 && codepoints[i-1] == '_') {
                    sb.appendCodePoint(c);
                } else {
                    sb.appendCodePoint(Character.toLowerCase(c));
                }
            }

            logger.info("Registering recipe handler {}", sb.toString());
            RecipeMap entry = (RecipeMap) field.get(null);
            Recipes.registerRecipeHandler(sb.toString(), new HandlerWrapper(parsed -> {
                StringBuilder p = new StringBuilder();
                if (!parsed.getInputs().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getInputs());
                    p.append(" inputs");
                }
                if (!parsed.getOutputs().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getOutputs());
                    p.append(" outputs");
                }
                if (!parsed.getInputFluids().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getInputFluids());
                    p.append(" fluid inputs");
                }
                if (!parsed.getOutputFluids().isEmpty()) {
                    if (p.length() > 0) {
                        p.append(", ");
                    }
                    namedList(p, parsed.getOutputFluids());
                    p.append(" fluid outputs");
                }
                p.append(" requiring ")
                        .append(parsed.getEu()).append(" EUs and taking ")
                        .append(parsed.getDuration()).append(" ticks");

                logger.info("Registering recipe for handler {} with {}", sb.toString(), p.toString());
                entry.recipeBuilder()
                        .duration(parsed.getDuration())
                        .EUt(parsed.getEu())
                        .inputs(parsed.getInputs())
                        .fluidInputs(parsed.getInputFluids())
                        .outputs(parsed.getOutputs())
                        .fluidOutputs(parsed.getOutputFluids())
                        .buildAndRegister();
            }));
        }


        Recipes.registerRecipeHandler("gtce_multiple", new HandlerWrapper(null) {
            @Override
            public BoxedUnit apply(ItemStack output, Config recipe) {
                List<? extends Config> alternatives = recipe.getConfigList("alternatives");
                for (Config altconf : alternatives) {
                    String type = "shaped";
                    if (altconf.hasPath("type")) {
                        type = altconf.getString("type");
                    }
                    Option<Function2<ItemStack, Config, BoxedUnit>> func = Recipes.recipeHandlers().get(type);
                    if (func.isDefined()) {
                        logger.info("Registering recipe alternative for handler {} and output {}", type, output.getDisplayName());
                        func.get().apply(output, altconf);
                    }
                }
                return BoxedUnit.UNIT;
            }
        });
    }

    public static ParsedRecipe parseRecipe(ItemStack primaryOutput, Config recipe) {
        List<ItemStack> inputs = parseIngredientList(recipe.getValue("input"));
        primaryOutput.setCount(Recipes.tryGetCount(recipe));
        if (inputs.size() < 1 || inputs.size() > 2) {
            throw new Recipes.RecipeException("Invalid recipe length: " + inputs.size() + ", should be 1 or 2.");
        }
        List<Integer> inputCount = recipe.getIntList("count");
        if (inputCount.size() != inputs.size()) {
            throw new Recipes.RecipeException("Mismatched ingredient count: " + inputs.size() + " != " + inputCount.size() + ".");
        }
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack input = inputs.get(i);
            if (input == null) {
                continue;
            }
            int count = inputCount.get(i);
            if (count <= 0) {
                continue;
            }
            input.setCount(Math.min(input.getMaxStackSize(), count));
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(primaryOutput);

        if (recipe.hasPath("secondaryOutput")) {
            List<ItemStack> secondaryOutputs = parseIngredientList(recipe.getValue("secondaryOutput"));
            List<Integer> secondaryOutputCount = recipe.getIntList("secondaryOutputCount");
            if (secondaryOutputs.size() != secondaryOutputCount.size()) {
                throw new Recipes.RecipeException("Mismatched secoundary output count: " + secondaryOutputs.size() + " != " + secondaryOutputCount.size() + ".");
            }
            for (int i = 0; i < secondaryOutputs.size(); i++) {
                ItemStack secondaryOutput = secondaryOutputs.get(i);
                if (secondaryOutput == null) {
                    continue;
                }
                int count = secondaryOutputCount.get(i);
                if (count <= 0) {
                    continue;
                }
                secondaryOutput.setCount(Math.min(secondaryOutput.getMaxStackSize(), count));
                outputs.add(secondaryOutput);
            }
        }

        List<FluidStack> inputFluids = new ArrayList<>();
        if (recipe.hasPath("inputFluid")) {
            Option<FluidStack> stack = Recipes.parseFluidIngredient(recipe.getConfig("inputFluid"));
            if (stack.isDefined()) {
                inputFluids.add(stack.get());
            }
        }

        List<FluidStack> outputFluids = new ArrayList<>();
        if (recipe.hasPath("outputFluid")) {
            Option<FluidStack> stack = Recipes.parseFluidIngredient(recipe.getConfig("outputFluid"));
            if (stack.isDefined()) {
                outputFluids.add(stack.get());
            }
        }

        int eu = recipe.getInt("eu");
        int duration = recipe.getInt("duration");

        return new ParsedRecipe(inputs, inputFluids, outputFluids, outputs, eu, duration);
    }

    public static List<ItemStack> transformIngredient(Object ing) {
        if (ing instanceof ItemStack) {
            return Collections.singletonList(((ItemStack) ing).copy());
        } else if (ing instanceof String) {
            List<ItemStack> out = new ArrayList<>();
            String ref = ing.toString();
            String gtref = "";
            NonNullList<ItemStack> ores = NonNullList.create();
            Recipes.RecipeException orig = null;
            try {
                ores = OreDictionary.getOres(ref);
            } catch (Recipes.RecipeException e) {
                orig = e;
            }
            if (ores.isEmpty() && ref.contains("#")) {
                String[] parts = ref.split("#", 2);
                try {
                    ores = OreDictionary.getOres(parts[0]);
                } catch (Recipes.RecipeException e) {
                    if (orig == null) {
                        orig = e;
                    }
                }
                gtref = parts[1];
            }
            if (ores.isEmpty() && orig != null) {
                throw orig;
            }

            if (ores.size() > 1) {
                for (ItemStack o : ores) {
                    if (o.getItem() instanceof MetaItem) {
                        MetaItem.MetaValueItem item = ((MetaItem) o.getItem()).getItem(o);
                        if (gtref.equals(item.unlocalizedName)) {
                            out.add(o.copy());
                            return out;
                        }
                        continue;
                    }
                    out.add(o.copy());
                }
            } else {
                out.add(ores.get(0).copy());
            }
            return out;
        } else if (ing == null) {
            return Collections.emptyList();
        } else {
            throw new Recipes.RecipeException("Invalid ingredient type: " + ing.getClass().getName());
        }
    }

    public static List<ItemStack> parseIngredientList(ConfigValue list) {
        Object unwrapped = list.unwrapped();
        List<ItemStack> ingredients = new ArrayList<>();
        if (unwrapped instanceof List) {
            for (Object ing : (List)unwrapped) {
                if (ing instanceof String && ((String) ing).contains("#")) {
                    ingredients.addAll(transformIngredient(ing));
                } else {
                    ingredients.addAll(transformIngredient(Recipes.parseIngredient(ing)));
                }
            }
        } else {
            ingredients.addAll(transformIngredient(Recipes.parseIngredient(unwrapped)));
        }
        return ingredients;
    }
     */
}
