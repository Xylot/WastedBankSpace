/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2021, Riley McGee
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.wastedbankspace;

import com.google.inject.Provides;
import com.wastedbankspace.model.locations.*;
import com.wastedbankspace.model.*;
import com.wastedbankspace.ui.WastedBankSpacePanel;
import com.wastedbankspace.ui.overlay.OverlayImage;
import com.wastedbankspace.ui.overlay.StorageItemOverlay;

import static com.wastedbankspace.model.StorageLocations.isItemStorable;

import com.wastedbankspace.util.DelayedRunnable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


@Slf4j
@PluginDescriptor(
	name = "Wasted Bank Space"
)
public class WastedBankSpacePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private StorageItemOverlay storageItemOverlay;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private WastedBankSpaceConfig config;

	@Inject
	private ConfigManager configManager;

	@Provides
	WastedBankSpaceConfig provideConfig(ConfigManager configManager)
	{

		return configManager.getConfig(WastedBankSpaceConfig.class);
	}

	private static final BufferedImage ICON = ImageUtil.loadImageResource(WastedBankSpacePlugin.class, "/overlaySmoll.png");
	public static final String CONFIG_GROUP = "Wasted Bank Space";
	private static boolean prepared = false;

	private final List<StorageLocationEnabler> storageLocationEnablers = Arrays.asList(
			new StorageLocationEnabler(() -> config.tackleBoxStorageCheck(), TackleBox.values()),
			new StorageLocationEnabler(() -> config.steelKeyRingStorageCheck(), SteelKeyRing.values()),
			new StorageLocationEnabler(() -> config.toolLeprechaunStorageCheck(), ToolLeprechaun.values()),
			new StorageLocationEnabler(() -> config.masterScrollBookStorageCheck(), MasterScrollBook.values()),
			new StorageLocationEnabler(() -> config.fossilStorageStrorageCheck(), FossilStorage.values()),
			new StorageLocationEnabler(() -> config.elnockInquisitorStorageCheck(), ElnockInquisitor.values()),
			new StorageLocationEnabler(() -> config.flamtaerBagStorageCheck(), FlamtaerBag.values()),
			new StorageLocationEnabler(() -> config.nightmareZoneStorageCheck(), NightmareZone.values()),
			new StorageLocationEnabler(() -> config.seedVaultStorageCheck(), SeedVault.values()),
			new StorageLocationEnabler(() -> config.treasureChestStorageCheck(), TreasureChest.values()),
			new StorageLocationEnabler(() -> config.fancyDressBoxStorageCheck(), FancyDressBox.values()),
			new StorageLocationEnabler(() -> config.magicWardrobeStorageCheck(), MagicWardrobe.values()),
			new StorageLocationEnabler(() -> config.toyBoxStorageCheck(), ToyBox.values()),
			new StorageLocationEnabler(() -> config.spiceRackStorageCheck(), SpiceRack.values()),
			new StorageLocationEnabler(() -> config.forestryKitStorageCheck(), ForestryKit.values()),
			new StorageLocationEnabler(() -> config.armourCaseStorageCheck(), ArmourCase.values()),
			new StorageLocationEnabler(() -> config.mysteriousStrangerStorageCheck(), MysteriousStranger.values()),
			new StorageLocationEnabler(() -> config.petHouseStorageCheck(), PetHouse.values()),
			new StorageLocationEnabler(() -> config.bookcaseStorageCheck(), Bookcase.values()),
			new StorageLocationEnabler(() -> config.capeRackStorageCheck(), CapeRack.values()),
			new StorageLocationEnabler(() -> config.huntsmansKitStorageCheck(), HuntsmansKit.values())
	);

	private List<Integer> unflaggedItemIds = new ArrayList<>();

	private NavigationButton navButton;
	private WastedBankSpacePanel panel;
	private Map<Integer, Integer> inventoryMap = new HashMap<>();

	private boolean isBankOpen = false;

	@Override
	protected void startUp() throws Exception
	{
		panel = new WastedBankSpacePanel(client, tooltipManager, config, itemManager, new Consumer<String>() {
			@Override
			public void accept(String s) {
				processBlackListChanged(s);
			}
		});
		navButton = NavigationButton.builder()
				.tooltip("Wasted Bank Space")
				.priority(8)
				.icon(ICON)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(storageItemOverlay);

		if (!prepared)
		{
			clientThread.invoke(() ->
			{
				switch (client.getGameState())
				{
					case LOGIN_SCREEN:
					case LOGIN_SCREEN_AUTHENTICATOR:
					case LOGGING_IN:
					case LOADING:
					case LOGGED_IN:
					case CONNECTION_LOST:
					case HOPPING:
						StorageLocations.prepareStorableItemNames(itemManager);
						panel.updatePluginFilter();
						prepared = true;
						return true;
					default:
						return false;
				}
			});
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(storageItemOverlay);

		navButton = null;
		panel = null;
	}

	public OverlayImage getOverlayImage()
	{
		return config.overlayImage();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged ev)
	{
		if (ev.getContainerId() == InventoryID.BANK.getId())
		{
			updateItemsFromBankContainer(ev.getItemContainer());
			isBankOpen = true;
		}
		else
		{
			isBankOpen = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}
		updateWastedBankSpace();
	}

	@Subscribe
	public void onMenuOpened(final MenuOpened event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) || !isBankOpen)
		{
			return;
		}

		final MenuEntry[] entries = event.getMenuEntries();

		for(int i = entries.length - 1; i >= 0; i--)
		{
			final MenuEntry entry = entries[i];
			final Widget w = entry.getWidget();

			if(w != null && (WidgetUtil.componentToInterface(w.getId()) == InterfaceID.BANK))
			{
				final int itemId = w.getItemId();
				final boolean flagged = !unflaggedItemIds.contains(itemId);
				if(isItemStorable(itemId))
				{
					final MenuEntry parent = client.createMenuEntry(i)
							.setOption(flagged ? "Unflag Item" : "Flag Item")
							.setTarget(entry.getTarget())
							.setType(MenuAction.RUNELITE)
							.onClick(x -> blacklistItem(itemId, flagged));
				}
				return;
			}
		}
	}

	private void blacklistItem(int id, boolean flagged)
	{
		//flagged = unflaggedItemIds !contains item
		if(!flagged)
		{
			if(unflaggedItemIds.contains(id))
			{
				unflaggedItemIds.remove((Integer)id);
				panel.removeFilteredItem(StorageLocations.getStorableItemName(id), id);
			}
		}
		else
		{

			if(!unflaggedItemIds.contains(id))
			{
				unflaggedItemIds.add((Integer)id);
				panel.addFilteredItem(StorageLocations.getStorableItemName(id));
			}
		}
	}

	private void updateItemsFromBankContainer(final ItemContainer c)
	{
		// Check if the contents have changed.
		if (c == null)
		{
			return;
		}

		final Map<Integer, Integer> m = new HashMap<>();
		for (Item item : c.getItems())
		{
			if (item.getId() == -1)
			{
				continue;
			}

			// Account for noted items, ignore placeholders.
			int itemID = item.getId();
			final ItemComposition itemComposition = itemManager.getItemComposition(itemID);
			if (itemComposition.getPlaceholderTemplateId() != -1)
			{
				continue;
			}

			if (itemComposition.getNote() != -1)
			{
				itemID = itemComposition.getLinkedNoteId();
			}

			final int qty = m.getOrDefault(itemID, 0) + item.getQuantity();
			m.put(itemID, qty);
		}

		inventoryMap = m;
		updateWastedBankSpace();
	}


	private void updateWastedBankSpace()
	{
		List<StorableItem> storableItemsInBank = new ArrayList<>();
		for (StorableItem item:
				getEnabledItemLists()) {
			int id = item.getItemID();
			if(inventoryMap.containsKey(id))
			{
				storableItemsInBank.add(item);
			}
		}

		SwingUtilities.invokeLater(
				() -> panel.setWastedBankSpaceItems(storableItemsInBank)
		);
	}

	public void processBlackListChanged(String fliter)
	{
		List<String> nonFlaggedItemList = Text.fromCSV(fliter);

		unflaggedItemIds = new ArrayList<>();
		for(String rule : nonFlaggedItemList)
		{
			if(rule.replaceAll("\\s+", "").matches("^\\d+$"))
			{
				unflaggedItemIds.add(Integer.parseInt(rule));
			}
			else
			{
				//Likely slow, should link each item in the text list to a key and find what keys changed/added
				for (StorageLocationEnabler sle:
						storageLocationEnablers) {
					for(StorableItem item: sle.GetStorableItems()){
						String name = StorageLocations.getStorableItemName(item);
						if(name == null)
						{
							continue;
						}
						if (rule.replaceAll("\\s+", "")
								.equalsIgnoreCase(name.replaceAll("\\s+", ""))
								&& !unflaggedItemIds.contains(item.getItemID())) {
							unflaggedItemIds.add(item.getItemID());
						}
					}
				}
			}
		}

		updateWastedBankSpace();
	}

	public List<StorableItem>  getEnabledItemLists()
	{
		// ON change and subscribe for the above
		// End this needs to Change
		List<StorableItem> ret = new ArrayList<>();
		for (StorageLocationEnabler sle:
				storageLocationEnablers) {
			for(StorableItem item: sle.GetStorableItemsIfEnabled()){
				if (unflaggedItemIds.contains(item.getItemID())
					|| (item.isBis() && config.bisfilterEnabledCheck())
				) {
					continue;
				}
				ret.add(item);
			}
		}
		return ret;
	}
}
