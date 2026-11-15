package piolunson.addon;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import piolunson.addon.modules.Journal;
import piolunson.addon.modules.SpeedMine;

public class SpeedMineAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("SpeedMine");

    @Override
    public void onInitialize() {
        add(new SpeedMine());
        add(new Journal());
    }

    private void add(Module module) {
        Modules.get().add(module);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "piolunson.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("piolunson", "for-meteor-addon");
    }
}
