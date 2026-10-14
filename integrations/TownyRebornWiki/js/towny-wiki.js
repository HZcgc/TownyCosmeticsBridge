/* Towny Reborn Wiki update 20261014-journey-beta7 */
(function () {
  'use strict';
  var root = document.getElementById('towny-reborn-wiki');
  if (!root || root.dataset.ready === 'true') return;
  root.dataset.ready = 'true';

  var scriptURL = document.currentScript && document.currentScript.src;
  var localAssets = location.protocol === 'file:' || new URLSearchParams(location.search).get('assets') === 'local';
  var assetBase = window.TR_WIKI_ASSET_BASE || (localAssets ? 'Eligius/img/wiki/' : (scriptURL ? new URL('../img/wiki/', scriptURL).href : '/custom/templates/Eligius/img/wiki/'));
  root.style.setProperty('--tr-hero', "url('" + assetBase + "start-02-spawn-arrival.webp')");
  root.querySelectorAll('img[data-wiki-asset]').forEach(function (assetImage) {
    assetImage.src = assetBase + assetImage.dataset.wikiAsset;
  });
  var galleries = {
    start: [
      ['join-01-add-server.webp', '1. Click Add Server', 'Open Multiplayer and choose Add Server at the bottom of the screen.'],
      ['join-02-resource-pack-enabled.webp', '2. Enter the address and enable the pack', 'Use play.townyreborn.com and make sure the button says Server Resource Packs: Enabled before clicking Done.'],
      ['join-03-join-server.webp', '3. Join Towny Reborn', 'Select the saved Towny Reborn entry and click Join Server.'],
      ['start-01-server-selector.webp', 'Choose Towny Reborn', 'Use the hub selector to enter the Earth SMP.'],
      ['start-02-spawn-arrival.webp', 'Welcome to Spawn', 'Spawn is your safe starting point and central travel hub.'],
      ['start-03-guide-npc.webp', 'Meet the Guide', 'The Guide NPC points new players toward the main systems.'],
      ['start-04-earth-portal.webp', 'Enter Earth', 'Jump into the Earth portal for the permanent Towny world.'],
      ['start-05-rtp-npc.webp', 'Random Teleport', 'The RTP NPC provides a quick route into unexplored land.'],
      ['start-06-resource-npc.webp', 'Gather elsewhere', 'Use resource worlds for renewable materials instead of damaging Earth.']
    ],
    towny: [
      ['towny-01-new-command.webp', 'Create a town', 'Run /t new <name> while standing in your future homeblock.'],
      ['towny-02-confirm-cost.webp', 'Confirm the cost', 'Towny asks you to approve the $2,000 creation charge.'],
      ['towny-03-created.webp', 'Town founded', 'Your first protected 16×16 homeblock is now active.'],
      ['towny-04-deposit.webp', 'Fund the town bank', 'Claims and upkeep are paid from the town account.'],
      ['towny-05-claim.webp', 'Claim adjacent land', 'Stand in the next wilderness chunk and use /t claim.'],
      ['towny-06-invite.webp', 'Invite residents', 'Residents grow your town level, claims and outpost allowance.'],
      ['towny-07-town-board.webp', 'Read the town board', '/town shows residents, bank funds, claims and town status.'],
      ['towny-08-map-town.webp', 'See the town on the map', 'The text map shows your position and nearby claimed chunks.'],
      ['towny-09-map-plots.webp', 'Understand plot borders', 'Use the map before buying, selling or assigning a plot.'],
      ['towny-10-plot-for-sale.webp', 'Put a plot up for sale', 'The mayor sets a price for residents to claim.'],
      ['towny-11-plot-claim.webp', 'Claim a resident plot', 'Confirm the purchase while standing inside the offered chunk.'],
      ['towny-12-plot-trust.webp', 'Manage plot trust', 'Grant access only to players you trust with your builds.'],
      ['towny-13-outpost-claim.webp', 'Claim an outpost', 'Outposts create detached town land after the slot is unlocked.'],
      ['towny-14-outpost-map.webp', 'Track outposts and plots', 'The Towny map distinguishes remote outposts from the main town.'],
      ['towny-15-first-town.webp', 'Your first town is ready', 'Keep recruiting, funding and shaping your settlement.']
    ],
    economy: [
      ['economy-01-balance.webp', 'Check your balance', 'Every new player starts with $100.'],
      ['economy-02-jobs-browse.webp', 'Browse professions', 'Compare job tasks and check the active-job limit for your rank.'],
      ['economy-03-job-join.webp', 'Join a job', 'Select a profession and begin earning from matching actions.'],
      ['economy-04-jobs-stats.webp', 'Track job progress', 'View levels, experience and your current professions.'],
      ['economy-05-job-payout.webp', 'Receive grouped payouts', 'Money, XP and points are summarized while you work.'],
      ['economy-06-shop-created.webp', 'Create a chest shop', 'Left-click a chest while holding the product.'],
      ['economy-07-shop-sell-mode.webp', 'Choose the shop type', 'Configure whether players buy from you or sell to you.'],
      ['economy-08-shop-selling.webp', 'Stock a selling shop', 'Keep enough matching items inside the linked container.'],
      ['economy-09-shop-buy-confirm.webp', 'Confirm the quantity', 'QuickShop asks for the amount before completing a trade.'],
      ['economy-10-shop-buying.webp', 'Buy from another player', 'Left-click a public shop and follow the confirmation prompt.'],
      ['economy-11-purchase-tax.webp', 'Transaction complete', 'The interacting player pays the configured 5% tax.'],
      ['economy-12-shop-control.webp', 'Edit your shop', 'Right-click your own shop to change settings or remove it.'],
      ['economy-13-shop-warp-create.webp', 'Create a shop warp', 'A public shop entry costs $1,000.'],
      ['economy-14-shop-directory.webp', 'Browse player shops', '/shops lists destinations created by the community.'],
      ['economy-15-shop-warping.webp', 'Visit a seller', 'Pick a directory entry and teleport to the shop.'],
      ['economy-16-auction-list.webp', 'List an auction', 'Hold an item and set its price with /ah sell.'],
      ['economy-17-auction-house.webp', 'Browse the Auction House', 'Search active global listings and confirm purchases.']
    ],
    quests: [
      ['quests-01-npc.webp', 'Meet the Quests NPC', 'Click the Spawn NPC to open quests, or use /quests from anywhere.'],
      ['quests-03-quest-list.webp', 'Work through quest chains', 'Select an available objective; later steps unlock as earlier quests are completed.'],
      ['quests-04-objective.webp', 'Inspect the objective', 'Hover a quest to see its exact target, current progress and money reward.']
    ],
    worlds: [
      ['worlds-01-rtp-menu.webp', 'Open /rtp', 'Choose between permanent Earth and renewable resource worlds.'],
      ['worlds-02-earth-rtp.webp', 'Teleport on Earth', 'Find wilderness for a town, home or exploration.'],
      ['worlds-03-resource-menu.webp', 'Open resource destinations', 'Select the dimension containing the materials you need.'],
      ['worlds-04-resource-overworld.webp', 'Resource Overworld', 'Gather stone, wood, terrain blocks and ordinary ores.'],
      ['worlds-05-resource-nether.webp', 'Resource Nether', 'Mine renewable Nether materials away from permanent builds.'],
      ['worlds-06-resource-end.webp', 'Resource End', 'Explore a renewable End without altering the Dragon arena.']
    ],
    spawners: [
      ['spawner-01-placed.webp', 'Place the spawner', 'Spawner blocks retain their mob type after Silk Touch mining.'],
      ['spawner-02-upgrade-menu.webp', 'Right-click for upgrades', 'Range, delay and spawn amount progress independently.'],
      ['spawner-03-range-upgrade.webp', 'Upgrade range', 'Increase the activation and spawning range to 32 blocks.'],
      ['spawner-04-delay-upgrade.webp', 'Reduce delay', 'Cut the spawn cycle from 25 seconds down to 10.'],
      ['spawner-05-amount-upgrade.webp', 'Increase amount', 'Raise each successful cycle from four mobs to eight.'],
      ['spawner-06-max-range.webp', 'Maximum range', 'Level V reaches the configured 32-block maximum.'],
      ['spawner-07-max-delay.webp', 'Maximum speed', 'Level V uses a 10-second delay.'],
      ['spawner-08-max-amount.webp', 'Maximum amount', 'Level V attempts eight entities per cycle.'],
      ['spawner-09-mine-start.webp', 'Mine with the right tool', 'Use a Netherite Pickaxe carrying Silk Touch.'],
      ['spawner-10-mine-drop.webp', 'Recover the block', 'A correct tool returns the spawner instead of destroying it.'],
      ['spawner-11-inventory.webp', 'Move it safely', 'The recovered spawner keeps its identity in your inventory.'],
      ['spawner-12-farm-layout.webp', 'Design by chunk borders', 'This example distributes up to 32 spawners across four chunks.']
    ],
    fishing: [],
    dragon: [
      ['dragon-01-announcement.webp', 'The countdown begins', 'The first announcement allows registration three hours early.'],
      ['dragon-03-lobby.webp', 'Enter the event lobby', 'Registered players are moved in when the five-minute lobby opens.'],
      ['dragon-04-arena.webp', 'Prepare in the arena', 'The custom End arena is freshly cloned for every event.'],
      ['dragon-02-register.webp', 'Register from anywhere', 'Use /dragon join when the event is accepting fighters.'],
      ['dragon-05-crystals.webp', 'The crystals awaken', 'Summoning begins after the final countdown.'],
      ['dragon-06-fight-status.webp', 'Fight status', 'The action bar tracks fighters, spectators and remaining time.'],
      ['dragon-07-victory.webp', 'Victory', 'Defeat the Ancient Dragon before the 20-minute limit.'],
      ['dragon-08-results.webp', 'Rankings and MVPs', 'Damage, survival, crystals and explosions are celebrated.'],
      ['dragon-09-exit-portal.webp', 'Return to Spawn', 'The portal is rebuilt after victory and the arena closes shortly after.']
    ],
    skills: [
      ['skills-01-menu.webp', 'Open the skills menu', 'Browse every mcMMO skill and your current levels.'],
      ['skills-02-levels.webp', 'Understand progression', 'The menu explains XP requirements for the next level.'],
      ['skills-03-player-info.webp', 'Inspect your profile', 'See power level, skill totals and progression at a glance.'],
      ['skills-04-leaderboard.webp', 'Climb the leaderboard', 'Compare your skills with the strongest players on the server.']
    ]
  };

  galleries["fishing-menu"] = [["pyro-01-main-menu.webp", "The main fishing menu", "Use /fish menu to reach the fishing features.", [811, 297, 426, 536, 2048, 1133]]];
  galleries["fishing-level"] = [["pyro-02-stats.webp", "The stats menu", "The left XP bottle shows your level; the right book shows your fishing statistics.", [811, 362, 426, 410, 2048, 1133]], ["pyro-03-fishing-level.webp", "Your Pyro Fishing Level", "Hover over the XP bottle to check Fishing Level, XP and Skill Points. Screenshot values are an example.", [811, 362, 625, 410, 2048, 1133]], ["pyro-05-statistics.webp", "Catch totals and statistics", "Hover over the book to see your catch totals, rarity counts, sales and contest wins. Screenshot totals are an example.", [811, 362, 630, 447, 2048, 1133]]];
  galleries["fishing-codex"] = [["pyro-06-codex.webp", "The Fish Codex", "Select a rarity, then hover over a species for its fishing conditions.", [811, 341, 426, 450, 2048, 1133]]];
  galleries["fishing-bait"] = [["pyro-07-bait-shop.webp", "The bait shop", "Hover over bait for its required level, price and uses.", [811, 297, 426, 536, 2048, 1133]], ["pyro-04-bait-levels.webp", "Bait unlock levels", "River 5, Reef 15, Abyss 30 and Mythic 45. These use your Pyro Fishing Level.", [811, 362, 775, 410, 2048, 1133]]];
  galleries["fishing-money"] = [["pyro-08-scales.webp", "Fish Scales", "Add fish to the empty slots, choose a risk and use the anvil to weigh them.", [811, 297, 426, 536, 2048, 1133]], ["pyro-09-gutting.webp", "The Gutting Station", "Eligible fish can be consumed for extra Entropy.", [811, 297, 426, 536, 2048, 1133]]];
  galleries["fishing-skills"] = [["pyro-10-better-gutting.webp", "Better Gutting", "Improve the Entropy you receive from gutting fish.", [811, 319, 735, 493, 2048, 1133]], ["pyro-11-luck-of-the-catch.webp", "Luck of the Catch", "Improve the Entropy you receive from catches.", [811, 319, 863, 493, 2048, 1133]], ["pyro-12-master-augmenter.webp", "Master Augmenter", "Reduce the Entropy cost of crafting augments.", [811, 319, 776, 493, 2048, 1133]], ["pyro-13-totem-leader.webp", "Totem Leader", "Reduce Totem cooldown by catching fish.", [811, 319, 832, 493, 2048, 1133]]];
  galleries["fishing-augments"] = [["pyro-14-augments.webp", "The augment catalogue", "Hover over an augment to read its effect, requirements and recipe.", [811, 297, 426, 536, 2048, 1133]]];
  galleries["fishing-deliveries"] = [["pyro-15-deliveries.webp", "Your deliveries", "Unlocked empty slots have no current order. Upgrades are along the bottom.", [811, 297, 426, 536, 2048, 1133]]];
  galleries["fishing-bag"] = [["pyro-16-fish-bag.webp", "Your Fish Bag", "Store useful catches here for later sales or deliveries.", [811, 297, 426, 536, 2048, 1133]]];
  galleries["fishing"] = [["fishing-07-pond.webp", "Fishing with the community", "Find a spot at the Spawn pond with /warp fishing."], ["fishing-11-contest-board.png", "Contest times and joining", "Four ten-minute rounds daily. Win $1,500, $750 or $400 plus Entropy; other participants receive 250 Entropy.", [540, 185, 970, 730, 2048, 1133]], ["fishing-08-live-board.webp", "The live contest board", "The active challenge, remaining time and top three update at the pond.", [530, 285, 950, 570, 2048, 1133]], ["fishing-09-chat-update.webp", "Your contest score in chat", "Chat updates show the challenge, time left, leader and your score."]];
  galleries["catchball"] = [["catchball-recipe.webp", "CatchBall recipe", "4 diamonds, 4 snowballs and 1 Eye of Ender. Each ball has 3 uses."]];
  galleries["chat"] = [["chat-item-sharing.png", "Show your held item", "Type [i] or [item] in a chat message to share the item in your hand."]];
  galleries["frames"] = [["item-frames-visibility-glow.webp", "Invisible frames and coloured glow", "Hide the frame, keep the displayed item, and choose a glow colour."]];

  galleries["supporter-ranks"] = [["rank-member.png", "Member rank", "Default Member prefix."], ["rank-skeleton.png", "Skeleton rank", "Skeleton monthly Supporter prefix."], ["rank-creeper.png", "Creeper rank", "Creeper monthly Supporter prefix."], ["rank-blaze.png", "Blaze rank", "Blaze monthly Supporter prefix."], ["rank-dragon.png", "Dragon rank", "Dragon monthly Supporter prefix."], ["rank-wither.png", "Wither rank", "Wither monthly Supporter prefix."]];

  galleries["artmap"] = [["artmap-01-studio.png", "Open the Art Studio", "Use /art for painting help, recipes, palettes and saved artwork.", [810, 402, 430, 330, 2048, 1133]], ["artmap-02-dyes.png", "Dyes and painting colours", "Browse the in-game palette to see which items produce each colour.", [810, 360, 430, 410, 2048, 1133]], ["artmap-03-palette.png", "More palette choices", "ArtMap has several pages of colours and painting materials.", [810, 360, 430, 410, 2048, 1133]], ["artmap-04-palette.png", "Additional painting materials", "Use the in-game help to check the available tools and colours.", [810, 360, 430, 410, 2048, 1133]], ["artmap-05-easel.png", "The easel recipe", "The Art Studio shows the current easel crafting recipe.", [810, 360, 430, 410, 2048, 1133]], ["artmap-06-canvas.png", "The canvas recipe", "Make a canvas for your easel, then follow the painting help in /art.", [810, 360, 430, 410, 2048, 1133]]];
  galleries["enchants"] = [["enchants-01-sword.png", "Custom enchantments on a sword", "The custom names and grey descriptions explain Bane of Netherspawn and Thrifty. Item values in screenshots are examples."], ["enchants-02-shovel.png", "Custom enchantments on a tool", "Glass Breaker breaks glass instantly. Telekinesis moves block loot directly into your inventory."]];
  galleries["player-warps"] = [["shops-01-directory.png", "Browse public player shops", "Open /shops, select a destination and stay still while the teleport counts down.", [810, 295, 430, 540, 2048, 1133]]];
  galleries["chest-shops"] = [["shops-02-chest-stock.png", "Read a stocked chest-shop sign", "The sign shows the owner, product, available stock and price per item. These prices are examples.", [510, 270, 1220, 570, 2048, 1133]], ["economy-06-shop-created.webp", "Create a chest shop", "Hold the product, left-click the chest and enter the unit price in chat."], ["economy-12-shop-control.webp", "Edit your own chest shop", "Right-click your own shop to change its settings. Shop controls are separate from your public warp settings."]];
  galleries["auctions"] = [["auctions-01-list.png", "Browse /ah", "Search listings and hover over an item before deciding to buy.", [810, 295, 430, 540, 2048, 1133]], ["auctions-02-tooltip.png", "Check quantity, price, seller and expiry", "This example lists 64 Blaze Rods for $200 in total. Left-click starts the purchase.", [810, 295, 485, 540, 2048, 1133]], ["auctions-03-confirm.png", "Confirm or cancel", "Check the item, then choose the green area to confirm or the red area to cancel.", [810, 317, 430, 495, 2048, 1133]]];
  galleries["auction-displays"] = [["auctions-04-displays.png", "Live auction displays at Spawn", "A listed item can be purchased by right-clicking its display. Empty displays link back to /ah."]];
  galleries["graves"] = [["graves-01-owner-timer.png", "Your protected grave", "The hologram shows the owner and remaining time. Graves last three hours; after expiry, their items drop.", [645, 275, 785, 485, 2048, 1133]]];
  galleries.economy = galleries.economy.slice(0, 5);

  function frameImage(frame, img, item) {
    var crop = item[3];
    frame.classList.toggle('is-cropped', !!crop);
    if (crop) {
      frame.style.setProperty('--frame-ratio', crop[2] / crop[3]);
      frame.style.setProperty('--frame-natural-width', crop[2] + 'px');
      frame.style.setProperty('--source-width', (crop[4] / crop[2] * 100) + '%');
      frame.style.setProperty('--source-left', (-crop[0] / crop[2] * 100) + '%');
      frame.style.setProperty('--source-top', (-crop[1] / crop[3] * 100) + '%');
    } else {
      frame.removeAttribute('style');
    }
    img.src = assetBase + item[0];
    img.alt = item[1] + ' — ' + item[2];
  }
  root.querySelectorAll('[data-rank-preview]').forEach(function (button) {
    button.addEventListener('click', function () {
      openLightbox(galleries['supporter-ranks'], Number(button.dataset.rankPreview));
    });
  });

  var lightbox = root.querySelector('#trw-lightbox');
  var lightboxImage = lightbox.querySelector('img');
  var lightboxFrame = lightbox.querySelector('.trw-image-frame');
  var lightboxLabel = lightbox.querySelector('.trw-lightbox-label');
  var lastFocus = null;
  var modalItems = [];
  var modalIndex = 0;
  var oldOverflow = '';
  function showLightbox(nextIndex) {
    modalIndex = (nextIndex + modalItems.length) % modalItems.length;
    var item = modalItems[modalIndex];
    frameImage(lightboxFrame, lightboxImage, item);
    lightboxLabel.textContent = (modalIndex + 1) + ' / ' + modalItems.length + ' · ' + item[1];
    lightbox.querySelectorAll('.trw-lightbox-arrow').forEach(function (arrow) { arrow.hidden = modalItems.length < 2; });
  }
  function openLightbox(items, index) {
    lastFocus = document.activeElement;
    oldOverflow = document.body.style.overflow;
    modalItems = items;
    showLightbox(index);
    lightbox.classList.add('is-open');
    document.body.style.overflow = 'hidden';
    lightbox.querySelector('.trw-lightbox-close').focus();
  }
  function closeLightbox() {
    lightbox.classList.remove('is-open');
    document.body.style.overflow = oldOverflow;
    lightboxImage.removeAttribute('src');
    if (lastFocus) lastFocus.focus();
  }
  lightbox.querySelector('.trw-lightbox-close').addEventListener('click', closeLightbox);
  lightbox.querySelector('.trw-lightbox-arrow--prev').addEventListener('click', function () { showLightbox(modalIndex - 1); });
  lightbox.querySelector('.trw-lightbox-arrow--next').addEventListener('click', function () { showLightbox(modalIndex + 1); });
  lightbox.addEventListener('click', function (event) { if (event.target === lightbox) closeLightbox(); });
  document.addEventListener('keydown', function (event) {
    if (!lightbox.classList.contains('is-open')) return;
    if (event.key === 'Escape') { event.preventDefault(); closeLightbox(); }
    if (event.key === 'ArrowLeft') { event.preventDefault(); showLightbox(modalIndex - 1); }
    if (event.key === 'ArrowRight') { event.preventDefault(); showLightbox(modalIndex + 1); }
    if (event.key === 'Tab') {
      var buttons = Array.prototype.slice.call(lightbox.querySelectorAll('button')).filter(function (button) { return !button.hidden; });
      var first = buttons[0], last = buttons[buttons.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });

  root.querySelectorAll('[data-fish-image]').forEach(function (img) { img.src = assetBase + 'fish/' + encodeURIComponent(img.getAttribute('data-fish-image')); });

  root.querySelectorAll('[data-gallery]').forEach(function (container) {
    var key = container.getAttribute('data-gallery');
    var items = galleries[key] || [];
    if (!items.length) return;
    var index = 0;
    container.innerHTML = '<div class="trw-gallery-top"><h3>In-game walkthrough</h3><span class="trw-gallery-count" aria-live="polite"></span></div>' +
      '<div class="trw-gallery-stage"><button class="trw-gallery-image-button" type="button" aria-label="Open screenshot"><span class="trw-image-frame"><img loading="lazy" decoding="async" alt=""></span></button>' +
      '<button class="trw-gallery-arrow trw-gallery-arrow--prev" type="button" aria-label="Previous screenshot">‹</button><button class="trw-gallery-arrow trw-gallery-arrow--next" type="button" aria-label="Next screenshot">›</button>' +
      '<div class="trw-gallery-caption"><strong></strong><span></span></div></div><div class="trw-gallery-thumbs" aria-label="Screenshot thumbnails"></div>';
    container.querySelector('.trw-gallery-top h3').textContent = container.dataset.galleryTitle || 'In-game screenshots';
    if (items.length === 1) {
      container.querySelectorAll('.trw-gallery-arrow').forEach(function (arrow) { arrow.hidden = true; });
      container.querySelector('.trw-gallery-thumbs').hidden = true;
    }
    var mainImage = container.querySelector('.trw-gallery-stage img');
    var mainFrame = mainImage.parentNode;
    var captionTitle = container.querySelector('.trw-gallery-caption strong');
    var captionText = container.querySelector('.trw-gallery-caption span');
    var counter = container.querySelector('.trw-gallery-count');
    var thumbs = container.querySelector('.trw-gallery-thumbs');
    items.forEach(function (item, itemIndex) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'trw-gallery-thumb';
      button.setAttribute('aria-label', 'Show ' + item[1]);
      var frame = document.createElement('span');
      frame.className = 'trw-image-frame';
      var thumb = document.createElement('img');
      thumb.loading = 'lazy';
      thumb.decoding = 'async';
      frameImage(frame, thumb, item);
      thumb.alt = '';
      frame.appendChild(thumb);
      button.appendChild(frame);
      button.addEventListener('click', function () { show(itemIndex); });
      thumbs.appendChild(button);
    });
    function show(nextIndex) {
      index = (nextIndex + items.length) % items.length;
      var item = items[index];
      frameImage(mainFrame, mainImage, item);
      captionTitle.textContent = item[1];
      captionText.textContent = item[2];
      counter.textContent = String(index + 1).padStart(2, '0') + ' / ' + String(items.length).padStart(2, '0');
      container.querySelector('.trw-gallery-image-button').setAttribute('aria-label', 'Enlarge ' + item[1]);
      thumbs.querySelectorAll('.trw-gallery-thumb').forEach(function (thumb, thumbIndex) {
        thumb.classList.toggle('is-active', thumbIndex === index);
        thumb.setAttribute('aria-pressed', String(thumbIndex === index));
      });
      var active = thumbs.children[index];
      if (active) {
        var targetLeft = active.offsetLeft - ((thumbs.clientWidth - active.clientWidth) / 2);
        thumbs.scrollTo({ left: Math.max(0, targetLeft), behavior: 'smooth' });
      }
    }
    container.querySelector('.trw-gallery-arrow--prev').addEventListener('click', function () { show(index - 1); });
    container.querySelector('.trw-gallery-arrow--next').addEventListener('click', function () { show(index + 1); });
    container.querySelector('.trw-gallery-image-button').addEventListener('click', function () { openLightbox(items, index); });
    container.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowLeft') { event.preventDefault(); show(index - 1); }
      if (event.key === 'ArrowRight') { event.preventDefault(); show(index + 1); }
    });
    show(0);
  });

  root.addEventListener('click', function (event) {
    var copy = event.target.closest('[data-copy]');
    if (!copy) return;
    var value = copy.getAttribute('data-copy');
    if (!value) return;
    function copied() {
      var old = copy.innerHTML;
      copy.innerHTML = '✓ Copied';
      setTimeout(function () { copy.innerHTML = old; }, 1400);
    }
    function fallbackCopy() {
      var area = document.createElement('textarea');
      area.value = value;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      var success = false;
      try { success = document.execCommand('copy'); } catch (error) { /* leave the command visible */ }
      area.remove();
      if (success) copied();
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value).then(copied).catch(fallbackCopy);
    } else {
      fallbackCopy();
    }
  });

  var search = root.querySelector('#trw-search');
  var clearSearch = root.querySelector('#trw-clear-search');
  var results = root.querySelector('#trw-search-results');
  var empty = root.querySelector('#trw-empty');
  var sections = Array.prototype.slice.call(root.querySelectorAll('.trw-section'));
  function filterWiki() {
    var query = search.value.trim().toLowerCase();
    var visible = 0;
    sections.forEach(function (section) {
      var haystack = (section.getAttribute('data-search') + ' ' + section.textContent).toLowerCase();
      var matches = !query || query.split(/\s+/).every(function (term) { return haystack.indexOf(term) !== -1; });
      section.hidden = !matches;
      if (matches) visible++;
    });
    results.textContent = visible + (visible === 1 ? ' guide chapter' : ' guide chapters');
    clearSearch.hidden = !query;
    empty.style.display = visible ? 'none' : 'block';
  }
  filterWiki();
  search.addEventListener('input', filterWiki);
  clearSearch.addEventListener('click', function () { search.value = ''; filterWiki(); search.focus(); });

  var navLinks = Array.prototype.slice.call(root.querySelectorAll('.trw-nav a[href^="#"]'));
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        navLinks.forEach(function (link) { link.classList.toggle('is-active', link.getAttribute('href') === '#' + entry.target.id); });
      });
    }, { rootMargin: '-18% 0px -72% 0px', threshold: 0 });
    sections.forEach(function (section) { observer.observe(section); });
  }
})();

(function(){'use strict';var root=document.getElementById('towny-reborn-wiki');if(!root)return;var input=root.querySelector('#trw-enchant-search');if(!input)return;var groups=Array.from(root.querySelectorAll('.trw-enchant-group'));input.addEventListener('input',function(){var terms=input.value.trim().toLowerCase().split(/\s+/).filter(Boolean),count=0;groups.forEach(function(group){if(terms.length&&!group.hasAttribute('data-search-open'))group.setAttribute('data-search-open',group.open?'1':'0');var visible=0;group.querySelectorAll('[data-enchant-entry]').forEach(function(card){var match=terms.every(function(term){return card.textContent.toLowerCase().includes(term)});card.hidden=!match;if(match){visible++;count++}});group.hidden=!visible;if(terms.length&&visible)group.open=true;else if(!terms.length&&group.hasAttribute('data-search-open')){group.open=group.getAttribute('data-search-open')==='1';group.removeAttribute('data-search-open')}});root.querySelector('#trw-enchant-results').textContent=count+(count===1?' enchantment':' enchantments')});})();

(function(){'use strict';var root=document.getElementById('towny-reborn-wiki');if(!root)return;var input=root.querySelector('#trw-codex-search');if(!input)return;var groups=Array.from(root.querySelectorAll('.trw-codex-group'));input.addEventListener('input',function(){var terms=input.value.trim().toLowerCase().replace(/_/g,' ').split(/\s+/).filter(Boolean),count=0;groups.forEach(function(group){if(terms.length&&!group.hasAttribute('data-search-open'))group.setAttribute('data-search-open',group.open?'1':'0');var visible=0;group.querySelectorAll('[data-codex-entry]').forEach(function(card){var text=card.textContent.toLowerCase().replace(/_/g,' '),match=terms.every(function(term){return text.includes(term)});card.hidden=!match;if(match){visible++;count++}});group.hidden=!visible;if(terms.length&&visible)group.open=true;else if(!terms.length&&group.hasAttribute('data-search-open')){group.open=group.getAttribute('data-search-open')==='1';group.removeAttribute('data-search-open')}});root.querySelector('#trw-codex-results').textContent=count+(count===1?' fish species':' fish species')});})();

/* Reborn Journey calculator. Rates and curve come from the generated guide. */
(function () {
  'use strict';
  var root = document.getElementById('towny-reborn-wiki');
  var form = root && root.querySelector('[data-journey-calculator]');
  if (!form || form.dataset.calculatorReady === 'true') return;
  form.dataset.calculatorReady = 'true';
  var steps = Number(form.dataset.steps);
  var base = Number(form.dataset.base);
  var increase = Number(form.dataset.increase);
  var titles = JSON.parse(form.dataset.titles);
  var jp = form.querySelector('#trw-journey-jp');
  var target = form.querySelector('#trw-journey-target');
  var error = form.querySelector('#trw-journey-error');
  var result = form.querySelector('.trw-journey-results');
  var planner = form.querySelector('#trw-journey-plan');
  var activities = Array.prototype.slice.call(form.querySelectorAll('[data-journey-activity]'));
  var number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
  function totalRequired(level) {
    var n = Math.max(0, Math.min(steps, level) - 1);
    return base * n + increase * n * (n - 1) / 2;
  }
  function levelAt(points) {
    var low = 1, high = steps;
    while (low < high) {
      var middle = Math.floor((low + high + 1) / 2);
      if (totalRequired(middle) <= points) low = middle;
      else high = middle - 1;
    }
    return low;
  }
  function valid(input, optional) {
    var value = Number(optional && input.value === '' ? 0 : input.value);
    return Number.isSafeInteger(value) && input.checkValidity();
  }
  function update() {
    var coreValid = valid(jp, false) && valid(target, false);
    var planValid = activities.every(function (input) { return valid(input, true); });
    jp.setAttribute('aria-invalid', String(!valid(jp, false)));
    target.setAttribute('aria-invalid', String(!valid(target, false)));
    activities.forEach(function (input) { input.setAttribute('aria-invalid', String(!valid(input, true))); });
    error.hidden = coreValid && planValid;
    error.textContent = !coreValid ? 'Enter whole numbers: JP from 0 to 1,000,000,000,000 and a target level from 1 to ' + steps + '.' :
      'Enter whole activity counts from 0 to 1,000,000. Leave an activity blank to count it as zero.';
    result.hidden = !coreValid;
    if (!coreValid) {
      planner.textContent = 'Enter valid Journey Points and a target level to calculate your plan.';
      return;
    }
    var points = Number(jp.value);
    var chosen = Number(target.value);
    var level = levelAt(points);
    var complete = level === steps;
    var start = totalRequired(level);
    var stepSize = complete ? 0 : totalRequired(level + 1) - start;
    var percent = complete ? 100 : Math.min(100, Math.max(0, Math.floor((points - start) * 100 / stepSize)));
    form.querySelector('#trw-journey-level').textContent = 'Level ' + level + ' · ' + titles[level - 1];
    form.querySelector('#trw-journey-next').textContent = complete ? 'Current Journey complete · no further level available' :
      'Level ' + (level + 1) + ' · ' + number.format(totalRequired(level + 1) - points) + ' JP needed';
    form.querySelector('#trw-journey-target-result').textContent = 'Level ' + chosen + ' requires ' + number.format(totalRequired(chosen)) + ' total JP · ' +
      (points >= totalRequired(chosen) ? 'target reached' : number.format(totalRequired(chosen) - points) + ' JP still needed');
    form.querySelector('#trw-journey-percent').textContent = percent + '% completed · ' + (100 - percent) + '% remaining';
    var bar = form.querySelector('#trw-journey-bar');
    bar.value = percent;
    bar.textContent = percent + '%';
    form.querySelector('#trw-journey-step').textContent = complete ? 'All ' + steps + ' current levels reached. Future extensions may add more.' :
      number.format(points - start) + ' / ' + number.format(stepSize) + ' JP earned within this level';
    if (!planValid) {
      planner.textContent = 'Correct the highlighted activity count to calculate planned JP.';
      return;
    }
    var planned = activities.reduce(function (sum, input) {
      return sum + Math.min(Number(input.dataset.cap), Number(input.value || 0) * Number(input.dataset.rate));
    }, 0);
    planner.textContent = 'Planned gain: ' + number.format(planned) + ' JP · projected total ' + number.format(points + planned) +
      ' JP · projected Level ' + levelAt(points + planned);
  }
  form.addEventListener('input', update);
  form.addEventListener('submit', function (event) { event.preventDefault(); update(); });
  update();
})();
