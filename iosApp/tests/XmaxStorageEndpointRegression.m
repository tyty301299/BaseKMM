// Exercises the same COS endpoint selector that caused the device SIGABRT.
// No network request or API credentials are used.
#import <Foundation/Foundation.h>
#import <QCloudCOSXML/QCloudCOSXMLEndPoint.h>

int main(void) {
    @autoreleasepool {
        NSURL *url = [NSURL URLWithString:@"https://test-1250000000.cos.ap-singapore.myqcloud.com"];
        BOOL reproduced = NO;
        @try {
            QCloudEndPoint *old = [[QCloudEndPoint alloc] initWithLiteralURL:url];
            [old serverURLWithBucket:@"test-1250000000" appID:nil regionName:@"ap-singapore"];
        } @catch (NSException *exception) {
            reproduced = YES;
        }
        if (!reproduced) return 1;
        @try {
            QCloudCOSXMLEndPoint *fixed = [[QCloudCOSXMLEndPoint alloc] initWithLiteralURL:url];
            fixed.regionName = @"ap-singapore";
            fixed.useHTTPS = YES;
            for (QCloudEndPoint *endpoint in @[fixed, [fixed copy]]) {
                NSURL *resolved = [endpoint serverURLWithBucket:@"test-1250000000" appID:nil regionName:@"ap-singapore"];
                if (![resolved isEqual:url]) return 2;
            }
        } @catch (NSException *exception) {
            return 3;
        }
        puts("PASS: old endpoint throws; COS endpoint and copy preserve upload URL without throwing.");
    }
    return 0;
}
