<?php
declare(strict_types = 1);
namespace Commercetools;

use Cache\Adapter\Filesystem\FilesystemCachePool;
use Commercetools\Importer\Client\CachedTokenProvider;
use Commercetools\Importer\Client\ClientCredentialsConfig;
use Commercetools\Importer\Client\ClientCredentialTokenProvider;
use Commercetools\Importer\Client\ClientFactory;
use Commercetools\Importer\Client\Config;
use Commercetools\Importer\Client\MiddlewareFactory;
use Commercetools\Importer\Models\Project\ProjectModel;
use GuzzleHttp\Client;
use GuzzleHttp\HandlerStack;
use League\Flysystem\Adapter\Local;
use League\Flysystem\Filesystem;
use Monolog\Handler\StreamHandler;
use Monolog\Logger;

require __DIR__ . '/../vendor/autoload.php';

$authHandler = HandlerStack::create();
$authHandler->push(MiddlewareFactory::createLoggerMiddleware(new Logger('auth', [new StreamHandler('./logs/requests.log')])));

$authConfig = new ClientCredentialsConfig(
    [
        'options' => ['handler' => $authHandler],
        'clientId' => '',
        'clientSecret' => ''
    ]
);
$config = new Config();
$cacheDir = $authConfig->getCacheDir();
$filesystemAdapter = new Local($cacheDir);
$filesystem        = new Filesystem($filesystemAdapter);
$cache = new FilesystemCachePool(   $filesystem);

$client = ClientFactory::of()->createGuzzleClient(
    $config,
    [
        'oauth' => MiddlewareFactory::createOAuthMiddlewareForProvider(
            new CachedTokenProvider(
                new ClientCredentialTokenProvider(
                    new Client($authConfig->getClientOptions()),
                    $authConfig
                ),
                $cache
            )
        ),
        'logger' => MiddlewareFactory::createLoggerMiddleware(new Logger('client', [new StreamHandler('./logs/requests.log')]))
    ]
);

var_dump($config);

$response = $client->get('');

var_dump((string)$response->getBody());

//$client = ClientFactory::of()->createGuzzleClient(
//    $config,
//    [
//        'oauth' => MiddlewareFactory::createOAuthMiddlewareForProvider(
//            new RawTokenProvider(new TokenModel(''))
//        ),
//        'logger' => MiddlewareFactory::createLoggerMiddleware(new Logger('client', [new StreamHandler('./logs/requests.log')]))
//    ]
//);

$response = $client->get('/phpsphere-90');

$project = new ProjectModel(json_decode((string)$response->getBody()));

var_dump($project->getKey());
var_dump($project->getCreatedAt());
var_dump($project->getMessages());
var_dump($project->getShippingRateInputType());
var_dump($project);
var_dump(json_encode($project));

//$project = new ProjectModel();
//var_dump($project);
//var_dump($project->getKey());
