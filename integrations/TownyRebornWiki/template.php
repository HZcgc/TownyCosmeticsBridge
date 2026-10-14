<?php

/**
 * Towny Reborn theme customisation for NamelessMC 2.2.5.
 * Public branding removed under the written authorisation included with
 * this package.
 */
class Eligius_Template extends SmartyTemplateBase
{
    private array $_template;
    private Cache $_cache;
    private Language $_language;
    private User $_user;
    private Pages $_pages;

    public function __construct(Cache $cache, Language $language, User $user, Pages $pages)
    {
        $template = [
            'name' => 'Eligius',
            'version' => '1.2.0',
            'nl_version' => '2.2.5',
            'author' => '<a href="https://townyreborn.com" target="_blank" rel="noopener">Towny Reborn</a>',
        ];

        $template['path'] = (defined('CONFIG_PATH') ? CONFIG_PATH : '') . '/custom/templates/' . $template['name'] . '/';

        parent::__construct(
            $template['name'],
            $template['version'],
            $template['nl_version'],
            $template['author'],
            __DIR__
        );

        $this->_settings = ROOT_PATH . '/custom/templates/Eligius/template_settings/settings.php';

        $this->assets()->include([
            AssetTree::FONT_AWESOME,
            AssetTree::JQUERY,
            AssetTree::JQUERY_COOKIE,
            AssetTree::FOMANTIC_UI,
        ]);

        $this->getEngine()->addVariable('TEMPLATE', $template);
        $this->getEngine()->addVariable('FORUM_SPAM_WARNING_TITLE', $language->get('general', 'warning'));
        $this->getEngine()->addVariables([
            'DEFAULT_REVAMP_DARK_MODE' => true,
            'DEFAULT_REVAMP_NAVBAR_EXTRA_CLASSES' => 'inverted',
        ]);

        if (defined('AUTO_LANGUAGE_VALUE')) {
            $this->getEngine()->addVariable('AUTO_LANGUAGE_VALUE', AUTO_LANGUAGE_VALUE);
        }

        $this->_template = $template;
        $this->_cache = $cache;
        $this->_language = $language;
        $this->_user = $user;
        $this->_pages = $pages;
    }

    private function setting(string $name, string $default): string
    {
        $this->_cache->setCache('template_settings');

        if ($this->_cache->isCached($name)) {
            return (string) $this->_cache->retrieve($name);
        }

        $this->_cache->store($name, $default);
        return $default;
    }

    private function colour(string $value, string $fallback): string
    {
        return preg_match('/^#[0-9a-fA-F]{6}$/', $value) ? $value : $fallback;
    }

    public function onPageLoad()
    {
        $page_load = microtime(true) - PAGE_START_TIME;
        define('PAGE_LOAD_TIME', $this->_language->get('general', 'page_loaded_in', ['time' => round($page_load, 3)]));

        $this->addCSSFiles([
            $this->_template['path'] . 'css/custom.css?v=225' => [],
            $this->_template['path'] . 'css/eligius.css?v=112' => [],
            $this->_template['path'] . 'css/ghost.css?v=112' => [],
            $this->_template['path'] . 'css/towny-reborn.css?v=20261004' => [],
            $this->_template['path'] . 'css/towny-showcase.css?v=20261004-pyro2' => [],
        ]);

        $route = isset($_GET['route']) ? rtrim($_GET['route'], '/') : '/';

        // Keep the public Wiki deployable with the theme. NamelessMC has already
        // checked the custom page's permissions before calling onPageLoad().
        if ($route === '/wiki' || (defined('CUSTOM_PAGE') && strcasecmp(CUSTOM_PAGE, 'Wiki') === 0)) {
            $wiki_file = __DIR__ . '/towny/wiki.html';
            if (is_file($wiki_file) && is_readable($wiki_file)) {
                $wiki_content = file_get_contents($wiki_file);
                if ($wiki_content !== false) {
                    $this->getEngine()->addVariable('CONTENT', $wiki_content);
                    $this->addCSSFiles([
                        $this->_template['path'] . 'css/towny-wiki.css?v=20261014-journey-beta7' => [],
                    ]);
                    $this->addJSFiles([
                        $this->_template['path'] . 'js/towny-wiki.js?v=20261014-journey-beta7' => [],
                    ]);
                }
            }
        }

        $primary_colour = $this->colour($this->setting('customColor', '#ff2bbd'), '#ff2bbd');
        $secondary_colour = $this->colour($this->setting('secondaryColor', '#70ff35'), '#70ff35');

        $this->addCSSStyle(':root{--main-color:' . $primary_colour . ';--tr-primary:' . $primary_colour . ';--tr-secondary:' . $secondary_colour . ';}');

        $JSVariables = [
            'siteName' => Output::getClean(SITE_NAME),
            'siteURL' => URL::build('/'),
            'fullSiteURL' => URL::getSelfURL() . ltrim(URL::build('/'), '/'),
            'page' => PAGE,
            'avatarSource' => AvatarSource::getUrlToFormat(),
            'copied' => $this->_language->get('general', 'copied'),
            'cookieNotice' => $this->_language->get('general', 'cookie_notice'),
            'noMessages' => $this->_language->get('user', 'no_messages'),
            'newMessage1' => $this->_language->get('user', '1_new_message'),
            'newMessagesX' => $this->_language->get('user', 'x_new_messages'),
            'noAlerts' => $this->_language->get('user', 'no_alerts'),
            'newAlert1' => $this->_language->get('user', '1_new_alert'),
            'newAlertsX' => $this->_language->get('user', 'x_new_alerts'),
            'bungeeInstance' => $this->_language->get('general', 'bungee_instance'),
            'andMoreX' => $this->_language->get('general', 'and_x_more'),
            'onePlayerOnline' => $this->_language->get('general', 'currently_1_player_online'),
            'xPlayersOnline' => $this->_language->get('general', 'currently_x_players_online'),
            'noPlayersOnline' => $this->_language->get('general', 'no_players_online'),
            'offline' => $this->_language->get('general', 'offline'),
            'confirmDelete' => $this->_language->get('general', 'confirm_deletion'),
            'debugging' => (defined('DEBUGGING') && DEBUGGING == 1) ? '1' : '0',
            'loggedIn' => $this->_user->isLoggedIn() ? '1' : '0',
            'cookie' => defined('COOKIE_NOTICE') ? '1' : '0',
            'loadingTime' => Settings::get('page_loading') === '1' ? PAGE_LOAD_TIME : '',
            'route' => $route,
            'csrfToken' => Token::get(),
            'customColor' => $primary_colour,
            'secondaryColor' => $secondary_colour,
            'ip_mc' => $this->setting('ipMC', 'play.townyreborn.com'),
            'ip_discord' => $this->setting('ipDiscord', 'discord.townyreborn.com'),
            'discord_id' => $this->setting('discordID', ''),
            'discord_url' => $this->setting('discordURL', 'https://discord.townyreborn.com'),
        ];

        $logo_cache = new Cache([
            'name' => 'nameless',
            'extension' => '.cache',
            'path' => ROOT_PATH . '/cache/',
        ]);
        $logo_cache->setCache('backgroundcache');
        $logo_image = $logo_cache->retrieve('logo_image');
        $JSVariables['logoImage'] = !empty($logo_image) ? $logo_image : null;

        if (str_contains($route, '/forum/topic/') || PAGE === 'profile') {
            $this->assets()->include([AssetTree::JQUERY_UI]);
        }

        $JSVars = '';
        $i = 0;
        foreach ($JSVariables as $var => $value) {
            $JSVars .= ($i === 0 ? 'const ' : ', ') . $var . ' = ' . json_encode($value);
            $i++;
        }
        $this->addJSScript($JSVars);

        $this->addJSFiles([
            $this->_template['path'] . 'js/core/core.js?v=225' => [],
            $this->_template['path'] . 'js/core/user.js?v=225' => [],
            $this->_template['path'] . 'js/core/pages.js?v=225' => [],
            $this->_template['path'] . 'js/scripts.js?v=112' => [],
            $this->_template['path'] . 'js/towny-showcase.js?v=20261004' => [],
        ]);

        foreach ($this->_pages->getAjaxScripts() as $script) {
            $this->addJSScript('$.getJSON(' . json_encode($script) . ', function(data) {});');
        }
    }
}

/**
 * @var Cache    $cache
 * @var Language $language
 * @var User     $user
 * @var Pages    $pages
 */
$template = new Eligius_Template($cache, $language, $user, $pages);
$template_pagination = ['div' => 'ui mini pagination menu', 'a' => '{x}item'];
