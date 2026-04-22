import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep2Component } from './claim-step2.component';

describe('ClaimStep2Component', () => {
  let component: ClaimStep2Component;
  let fixture: ComponentFixture<ClaimStep2Component>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep2Component]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep2Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
