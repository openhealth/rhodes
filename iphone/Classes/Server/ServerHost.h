//
//  ServerHost.h
//  Browser
//
//  Created by adam blum on 9/10/08.
//  Copyright 2008 __MyCompanyName__. All rights reserved.
//

#import "AppManager.h"
#import "SyncEngine.h"

@interface ServerHost : NSObject
{
	AppManager * appManager;
    CFRunLoopRef runLoop;
	sqlite3 *database;
	NSString *homeUrl;
	
@public
	id  actionTarget;
	SEL onStartSuccess;
	SEL onStartFailure;
	SEL onRefreshView;
	SEL onNavigateTo;
	SEL onSetViewHomeUrl;
	SEL onTakePicture;
	SEL onChoosePicture;
}

// callbacks
@property (assign) id  actionTarget;
@property (assign) SEL onStartSuccess;
@property (assign) SEL onStartFailure;
@property (assign) SEL onRefreshView;
@property (assign) SEL onNavigateTo;
@property (assign) SEL onSetViewHomeUrl;
@property (assign) SEL onTakePicture;
@property (assign) SEL onChoosePicture;

- (void) start;
//TODO - implement pause and stop operations and graceful termination of the thread...
- (void) stop;

+ (ServerHost *)sharedInstance;

@end